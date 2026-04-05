package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Vec2;
import dev.engine.core.property.PropertyKey;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.core.scene.MeshTag;
import dev.engine.core.scene.camera.Camera;
import dev.engine.graphics.shader.GlobalParamsRegistry;
import dev.engine.graphics.shader.params.CameraParams;
import dev.engine.graphics.shader.params.EngineParams;
import dev.engine.core.transaction.Transaction;
import dev.engine.graphics.*;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.core.mesh.MeshData;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.renderer.MeshRenderer;
import dev.engine.graphics.renderer.Renderable;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.graphics.shader.ShaderCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The single public entry point for all rendering operations.
 *
 * <p>Orchestrates the rendering pipeline by delegating to focused managers:
 * {@link MeshManager}, {@link TextureManager}, {@link UniformManager},
 * {@link RenderStateManager}, and {@link ShaderManager}.
 */
public class Renderer implements AutoCloseable {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Renderer.class);

    private final RenderDevice device;
    private final GpuResourceManager gpu;
    private final MeshRenderer meshRenderer;
    private final MeshManager meshManager;
    private final TextureManager textureManager;
    private final UniformManager uniformManager;
    private final RenderStateManager renderStateManager;
    private final RenderTargetManager renderTargetManager;
    private final PipelineManager pipelineManager;
    private final SamplerManager samplerManager;
    private final ShaderManager shaderManager;

    private final List<Runnable> postSceneCallbacks = new ArrayList<>();
    private final List<Camera> cameras = new ArrayList<>();
    private Camera activeCamera;
    private Handle<PipelineResource> defaultPipeline;

    // Engine timing
    private float engineTime = 0f;
    private float lastDeltaTime = 0f;
    private int frameCount = 0;



    // Viewport
    private Viewport viewport = Viewport.of(1, 1); // Placeholder until window sets actual size

    // Clear color
    private float clearR = 0.05f, clearG = 0.05f, clearB = 0.08f, clearA = 1f;

    public Renderer(RenderDevice device, ShaderCompiler compiler) {
        this.device = device;
        this.gpu = new GpuResourceManager(device);
        this.meshRenderer = new MeshRenderer();
        this.meshManager = new MeshManager(gpu);
        this.textureManager = new TextureManager(gpu);
        this.renderStateManager = new RenderStateManager();
        this.renderTargetManager = new RenderTargetManager(gpu);
        this.pipelineManager = new PipelineManager(gpu);
        this.samplerManager = new SamplerManager(gpu);

        var globalParams = new GlobalParamsRegistry();
        globalParams.register("Engine", EngineParams.class, 0);
        globalParams.register("Camera", CameraParams.class, 1);
        globalParams.register("Object", dev.engine.graphics.shader.params.ObjectParams.class, 2);

        this.uniformManager = new UniformManager(gpu, globalParams);
        this.shaderManager = new ShaderManager(device, globalParams, compiler);
    }

    public static Renderer createHeadless() {
        return new Renderer(new HeadlessRenderDevice(), new NoOpShaderCompiler());
    }

    // --- Camera management ---

    public Camera createCamera() {
        var cam = new Camera();
        cameras.add(cam);
        if (activeCamera == null) activeCamera = cam;
        return cam;
    }

    public void setActiveCamera(Camera camera) { this.activeCamera = camera; }
    public Camera activeCamera() { return activeCamera; }

    // --- Mesh management (delegates to MeshManager) ---

    public Handle<MeshTag> createMesh(float[] vertices, int[] indices, VertexFormat format) {
        return meshManager.createMesh(vertices, indices, format);
    }

    public Handle<MeshTag> createMeshFromData(MeshData data) {
        return meshManager.createMeshFromData(data);
    }

    // --- Shader management ---

    public ShaderManager shaderManager() { return shaderManager; }

    public void setDefaultPipeline(Handle<PipelineResource> pipeline) {
        this.defaultPipeline = pipeline;
    }

    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        return pipelineManager.create(descriptor);
    }

    // --- Global params (delegates to UniformManager) ---

    public <T extends Record> void registerGlobalParams(String name, Class<T> recordType, int binding) {
        uniformManager.registerGlobalParams(name, recordType, binding);
        shaderManager.invalidateAll();
    }

    public <T extends Record> void registerGlobalParams(String name, Class<T> recordType) {
        registerGlobalParams(name, recordType, uniformManager.globalParams().nextBinding());
    }

    public void updateGlobalParams(String name, Object data) {
        uniformManager.updateGlobalParams(name, data);
    }

    public GlobalParamsRegistry globalParams() { return uniformManager.globalParams(); }

    // --- Viewport ---

    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
    }

    public void setViewport(int width, int height) {
        this.viewport = Viewport.of(width, height);
    }

    public Viewport viewport() { return viewport; }

    public void setClearColor(float r, float g, float b, float a) {
        this.clearR = r;
        this.clearG = g;
        this.clearB = b;
        this.clearA = a;
    }

    // --- Render state (delegates to RenderStateManager) ---

    public <T> void setDefault(PropertyKey<RenderState, T> key, T value) {
        renderStateManager.setDefault(key, value);
    }

    public <T> void forceProperty(PropertyKey<RenderState, T> key, T value) {
        renderStateManager.forceProperty(key, value);
    }

    public <T> void clearForced(PropertyKey<RenderState, T> key) {
        renderStateManager.clearForced(key);
    }

    /** Updates engine timing. Called by BaseApplication each frame. */
    public void updateTime(float time, float deltaTime) {
        this.engineTime = time;
        this.lastDeltaTime = deltaTime;
    }

    // --- Render ---

    public void renderFrame(List<Transaction> transactions) {
        // Poll stale GPU resources from weak caches
        meshManager.pollStale();
        textureManager.pollStale();

        // Process transactions
        meshRenderer.processTransactions(transactions);

        // Resolve mesh/material assignments → renderables
        for (var entity : meshRenderer.getEntities()) {
            if (meshRenderer.getRenderable(entity) != null) continue;

            MeshHandle resolvedMesh = null;
            var data = meshRenderer.getMeshData(entity);
            if (data != null) {
                resolvedMesh = meshManager.resolve(data);
            }
            if (resolvedMesh == null) {
                var meshHandle = meshRenderer.getMeshAssignment(entity);
                if (meshHandle != null) resolvedMesh = meshManager.resolve(meshHandle);
            }
            if (resolvedMesh == null) continue;

            var mat = meshRenderer.getMaterialData(entity);
            var compiled = shaderManager.resolveForEntity(entity, mat);

            var pipeline = compiled != null ? compiled.pipeline() : defaultPipeline;
            var bindings = compiled != null ? ShaderManager.extractBufferBindings(compiled) : Map.<String, Integer>of();

            meshRenderer.setRenderable(entity, new Renderable(
                    resolvedMesh.vertexBuffer(), resolvedMesh.indexBuffer(), resolvedMesh.vertexInput(),
                    pipeline, resolvedMesh.vertexCount(), resolvedMesh.indexCount(), bindings));
        }

        device.beginFrame();
        frameCount++;

        // Setup pass
        var setup = new CommandRecorder();
        setup.viewport(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        setup.setRenderState(RenderState.defaults());
        setup.clear(clearR, clearG, clearB, clearA);
        if (defaultPipeline != null) {
            setup.bindPipeline(defaultPipeline);
        }
        device.submit(setup.finish());

        // Update engine params
        uniformManager.updateGlobalParams("Engine", new EngineParams(engineTime, lastDeltaTime,
                new Vec2(viewport.width(), viewport.height()), frameCount));

        // Upload camera params
        if (activeCamera != null) {
            uniformManager.updateGlobalParams("Camera", new CameraParams(
                    activeCamera.viewProjectionMatrix(),
                    activeCamera.viewMatrix(),
                    activeCamera.projectionMatrix(),
                    activeCamera.position(),
                    activeCamera.nearPlane(), activeCamera.farPlane()));
        }

        uniformManager.uploadPerFrameGlobals();

        // Draw each object
        if (activeCamera != null) {
            for (var cmd : meshRenderer.collectBatch()) {
                if (cmd.renderable().pipeline() == null) continue;

                var objectUbo = uniformManager.uploadObjectParams(cmd.entity(), cmd.transform());

                var draw = new CommandRecorder();
                draw.bindPipeline(cmd.renderable().pipeline());

                var mat = meshRenderer.getMaterialData(cmd.entity());
                var renderState = renderStateManager.resolve(mat);
                draw.setRenderState(renderState);

                uniformManager.bindGlobals(draw, cmd.renderable(), objectUbo);

                if (mat != null) {
                    int materialSlot = cmd.renderable().bindingFor("MaterialBuffer",
                            uniformManager.globalParams().nextBinding());
                    uniformManager.uploadAndBindMaterial(mat, cmd.entity(), draw, materialSlot);

                    var compiled = shaderManager.getEntityShader(cmd.entity());
                    if (compiled != null) {
                        textureManager.bindMaterialTextures(mat, compiled, samplerManager, draw);
                    }
                }

                draw.bindVertexBuffer(cmd.renderable().vertexBuffer(), cmd.renderable().vertexInput());
                if (cmd.renderable().indexBuffer() != null) {
                    draw.bindIndexBuffer(cmd.renderable().indexBuffer());
                    draw.drawIndexed(cmd.renderable().indexCount(), 0);
                } else {
                    draw.draw(cmd.renderable().vertexCount(), 0);
                }
                device.submit(draw.finish());
            }
        }

        // Run post-scene callbacks (e.g., debug UI overlay)
        for (var callback : postSceneCallbacks) {
            callback.run();
        }

        device.endFrame();
        gpu.processDeferred();
    }

    /** Registers a callback to run after scene rendering but before endFrame. */
    public void addPostSceneCallback(Runnable callback) {
        postSceneCallbacks.add(callback);
    }

    /** Removes a previously registered post-scene callback. */
    public void removePostSceneCallback(Runnable callback) {
        postSceneCallbacks.remove(callback);
    }

    // --- Capabilities ---

    public <T> T queryCapability(DeviceCapability<T> capability) {
        return device.queryCapability(capability);
    }

    public boolean supports(DeviceCapability<Boolean> feature) {
        Boolean result = device.queryCapability(feature);
        return result != null && result;
    }

    public String backendName() {
        return queryCapability(DeviceCapability.BACKEND_NAME);
    }

    // --- Low-level access ---

    public RenderDevice device() { return device; }
    public GpuResourceManager gpu() { return gpu; }
    public MeshManager meshManager() { return meshManager; }
    public TextureManager textureManager() { return textureManager; }
    public UniformManager uniformManager() { return uniformManager; }
    public RenderStateManager renderStateManager() { return renderStateManager; }
    public RenderTargetManager renderTargetManager() { return renderTargetManager; }
    public PipelineManager pipelineManager() { return pipelineManager; }
    public SamplerManager samplerManager() { return samplerManager; }

    @Override
    public void close() {
        uniformManager.close();
        textureManager.close();
        renderTargetManager.close();
        pipelineManager.close();
        samplerManager.close();
        if (defaultPipeline != null) gpu.destroyPipeline(defaultPipeline);
        gpu.shutdown();
        device.close();
    }
}
