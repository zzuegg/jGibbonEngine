package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.core.scene.Scene;
import dev.engine.core.scene.EntityTag;
import dev.engine.core.scene.camera.Camera;
import dev.engine.graphics.*;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.core.shader.SlangCompiler;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.common.material.Material;
import dev.engine.graphics.common.material.MaterialCompiler;
import dev.engine.graphics.common.material.MaterialType;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.renderer.DrawCommand;
import dev.engine.graphics.renderer.MeshRenderer;
import dev.engine.graphics.renderer.Renderable;
import dev.engine.graphics.vertex.VertexFormat;

import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single public entry point for all rendering operations.
 *
 * <p>Users create entities in the scene, assign meshes and transforms,
 * set up a camera, and call {@link #renderFrame()}. The renderer handles
 * everything else: command recording, UBO management, batching, submission.
 */
public class Renderer implements AutoCloseable {

    private final RenderDevice device;
    private final Scene scene;
    private final MeshRenderer meshRenderer;
    private final List<Camera> cameras = new ArrayList<>();
    private Camera activeCamera;

    // Shader management
    private final ShaderManager shaderManager;

    // Internal GPU resources
    private final Handle<BufferResource> mvpUbo;
    private final StructLayout mat4Layout = StructLayout.of(Mat4.class);
    private Handle<PipelineResource> defaultPipeline;

    // Entity → MeshHandle + Material mapping
    private final Map<Handle<EntityTag>, MeshHandle> entityMeshes = new HashMap<>();
    private final Map<Handle<EntityTag>, Material> entityMaterials = new HashMap<>();

    // Material data UBO (binding 1) — lazily created per material key
    private final Map<String, Handle<BufferResource>> materialUbos = new HashMap<>();

    // Viewport
    private int viewportWidth = 800;
    private int viewportHeight = 600;

    public Renderer(RenderDevice device) {
        this(device, SlangCompiler.find());
    }

    public Renderer(RenderDevice device, SlangCompiler slangCompiler) {
        this.device = device;
        this.scene = new Scene();
        this.meshRenderer = new MeshRenderer();
        this.shaderManager = new ShaderManager(slangCompiler, device);
        this.mvpUbo = device.createBuffer(
                new BufferDescriptor(mat4Layout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));

        // Auto-compile default unlit pipeline if Slang is available
        if (slangCompiler.isAvailable()) {
            try {
                this.defaultPipeline = shaderManager.getPipeline(MaterialType.UNLIT);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(Renderer.class)
                        .warn("Failed to compile default shader: {}", e.getMessage());
            }
        }
    }

    /**
     * Creates a headless renderer with a stub backend for testing.
     */
    public static Renderer createHeadless() {
        return new Renderer(new HeadlessRenderDevice());
    }

    // --- Scene access ---

    public Scene scene() { return scene; }

    // --- Camera management ---

    public Camera createCamera() {
        var cam = new Camera();
        cameras.add(cam);
        if (activeCamera == null) activeCamera = cam;
        return cam;
    }

    public void setActiveCamera(Camera camera) { this.activeCamera = camera; }
    public Camera activeCamera() { return activeCamera; }

    // --- Mesh management ---

    public MeshHandle createMesh(float[] vertices, int[] indices, VertexFormat format) {
        // Upload vertex data
        long vbSize = (long) vertices.length * Float.BYTES;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < vertices.length; i++) {
                w.segment().setAtIndex(ValueLayout.JAVA_FLOAT, i, vertices[i]);
            }
        }

        // Upload index data
        Handle<BufferResource> ibo = null;
        int indexCount = 0;
        if (indices != null && indices.length > 0) {
            long ibSize = (long) indices.length * Integer.BYTES;
            ibo = device.createBuffer(new BufferDescriptor(ibSize, BufferUsage.INDEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(ibo)) {
                for (int i = 0; i < indices.length; i++) {
                    w.segment().setAtIndex(ValueLayout.JAVA_INT, i, indices[i]);
                }
            }
            indexCount = indices.length;
        }

        // Create vertex input
        var vertexInput = device.createVertexInput(format);

        int vertexCount = vertices.length / (format.stride() / Float.BYTES);
        return new MeshHandle(vbo, ibo, vertexInput, format, vertexCount, indexCount);
    }

    public void setMesh(Handle<EntityTag> entity, MeshHandle mesh) {
        entityMeshes.put(entity, mesh);
    }

    // --- Material management ---

    public Material createMaterial(MaterialType type) {
        return Material.create(type);
    }

    public void setMaterial(Handle<EntityTag> entity, Material material) {
        entityMaterials.put(entity, material);
    }

    // --- Shader management ---

    public ShaderManager shaderManager() { return shaderManager; }

    public void setDefaultPipeline(Handle<PipelineResource> pipeline) {
        this.defaultPipeline = pipeline;
    }

    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        return device.createPipeline(descriptor);
    }

    // --- Viewport ---

    public void setViewport(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    // --- Render ---

    public void renderFrame() {
        // Process scene transactions
        meshRenderer.processTransactions(scene.drainTransactions());

        // Sync entity meshes → renderables, resolve pipeline from material
        for (var entry : entityMeshes.entrySet()) {
            var entity = entry.getKey();
            var mesh = entry.getValue();
            if (meshRenderer.hasEntity(entity) && meshRenderer.getRenderable(entity) == null) {
                var material = entityMaterials.get(entity);
                Handle<PipelineResource> pipeline = defaultPipeline;
                if (material != null) {
                    try {
                        if (material.shaderSource() != null) {
                            pipeline = shaderManager.compileSlangFile(material.shaderSource());
                        } else {
                            pipeline = shaderManager.getPipeline(material.type());
                        }
                    } catch (Exception e) {
                        // Fall back to default
                    }
                }
                meshRenderer.setRenderable(entity, new Renderable(
                        mesh.vertexBuffer(), mesh.indexBuffer(), mesh.vertexInput(),
                        pipeline, mesh.vertexCount(), mesh.indexCount()));
            }
        }

        device.beginFrame();

        // Setup pass
        var setup = new CommandRecorder();
        setup.viewport(0, 0, viewportWidth, viewportHeight);
        setup.setDepthTest(true);
        setup.setCullFace(true);
        setup.clear(0.05f, 0.05f, 0.08f, 1f);
        if (defaultPipeline != null) {
            setup.bindPipeline(defaultPipeline);
        }
        device.submit(setup.finish());

        // Draw each object
        if (activeCamera != null) {
            var vp = activeCamera.viewProjectionMatrix();
            for (var cmd : meshRenderer.collectBatch()) {
                // Skip if no pipeline
                if (cmd.renderable().pipeline() == null) continue;

                // Upload MVP to binding 0
                var mvp = vp.mul(cmd.transform());
                try (var w = device.writeBuffer(mvpUbo)) {
                    mat4Layout.write(w.segment(), 0, mvp);
                }

                var draw = new CommandRecorder();
                draw.bindPipeline(cmd.renderable().pipeline());
                draw.bindUniformBuffer(0, mvpUbo);

                // Upload material data to binding 1 if material has properties
                var entity = findEntityForRenderable(cmd);
                if (entity != null) {
                    var material = entityMaterials.get(entity);
                    if (material != null) {
                        uploadMaterialData(material, draw);
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

        device.endFrame();
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

    // --- Internal helpers ---

    private void uploadMaterialData(Material material, CommandRecorder draw) {
        if (material.hasRecordData()) {
            // Record-based: use StructLayout to serialize
            var record = material.data();
            var layout = dev.engine.core.layout.StructLayout.of(record.getClass());
            var key = "record_" + record.getClass().getName();
            var ubo = materialUbos.computeIfAbsent(key, k ->
                    device.createBuffer(new dev.engine.graphics.buffer.BufferDescriptor(
                            layout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC)));
            try (var w = device.writeBuffer(ubo)) {
                layout.write(w.segment(), 0, record);
            }
            draw.bindUniformBuffer(1, ubo);
        } else {
            // Property bag: use MaterialCompiler to serialize
            var data = MaterialCompiler.serializeMaterialData(material);
            if (data.remaining() > 0) {
                var key = MaterialCompiler.shaderKey(material);
                var ubo = materialUbos.computeIfAbsent(key, k ->
                        device.createBuffer(new dev.engine.graphics.buffer.BufferDescriptor(
                                data.remaining(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC)));
                try (var w = device.writeBuffer(ubo)) {
                    for (int i = 0; i < data.remaining(); i++) {
                        w.segment().set(java.lang.foreign.ValueLayout.JAVA_BYTE, i, data.get(i));
                    }
                }
                draw.bindUniformBuffer(1, ubo);
            }
        }
    }

    private Handle<EntityTag> findEntityForRenderable(dev.engine.graphics.renderer.DrawCommand cmd) {
        // Reverse lookup: find which entity owns this renderable's transform
        for (var entry : entityMeshes.entrySet()) {
            if (meshRenderer.hasEntity(entry.getKey()) &&
                    meshRenderer.getTransform(entry.getKey()) == cmd.transform()) {
                return entry.getKey();
            }
        }
        return null;
    }

    // --- Low-level access (escape hatch) ---

    public RenderDevice device() { return device; }

    @Override
    public void close() {
        device.destroyBuffer(mvpUbo);
        for (var ubo : materialUbos.values()) device.destroyBuffer(ubo);
        materialUbos.clear();
        if (defaultPipeline != null) device.destroyPipeline(defaultPipeline);
        device.close();
    }
}
