package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.core.handle.HandlePool;
import dev.engine.core.scene.AbstractScene;
import dev.engine.core.scene.MaterialTag;
import dev.engine.core.scene.MeshTag;
import dev.engine.core.scene.Scene;
import dev.engine.core.scene.SceneAccess;
import dev.engine.core.scene.EntityTag;
import dev.engine.core.scene.camera.Camera;
import dev.engine.graphics.*;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.core.shader.SlangCompiler;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.core.material.Material;
import dev.engine.core.material.MaterialType;
import dev.engine.graphics.common.material.MaterialCompiler;
import dev.engine.core.asset.TextureData;
import dev.engine.core.mesh.MeshData;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.renderer.DrawCommand;
import dev.engine.graphics.renderer.MeshRenderer;
import dev.engine.graphics.renderer.Renderable;
import dev.engine.core.mesh.VertexFormat;

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
    private final AbstractScene scene;
    private final MeshRenderer meshRenderer;
    private final List<Camera> cameras = new ArrayList<>();
    private Camera activeCamera;

    // Shader management
    private final ShaderManager shaderManager;

    // Internal GPU resources
    private final Handle<BufferResource> mvpUbo;
    private final StructLayout mat4Layout = StructLayout.of(Mat4.class);
    private Handle<PipelineResource> defaultPipeline;

    // Mesh registry: Handle<MeshTag> → MeshHandle (GPU resources)
    private final HandlePool<MeshTag> meshPool = new HandlePool<>();
    private final Map<Integer, MeshHandle> meshRegistry = new HashMap<>();

    // Material registry: Handle<MaterialTag> → Material
    private final HandlePool<MaterialTag> materialPool = new HandlePool<>();
    private final Map<Integer, Material> materialRegistry = new HashMap<>();

    // Auto-upload cache: MeshData identity hash → MeshHandle (GPU resources)
    private final Map<Integer, MeshHandle> meshDataCache = new HashMap<>();

    // Texture auto-upload cache: TextureData identity hash → GPU texture handle
    private final Map<Integer, Handle<TextureResource>> textureCache = new HashMap<>();

    // Material type → shader mapping (PbrMaterial.class → "PBR", etc.)
    private final Map<Class<? extends dev.engine.core.material.MaterialData>, String> materialShaderMap = new HashMap<>();

    // Material data UBO (binding 1) — lazily created per material key
    private final Map<String, Handle<BufferResource>> materialUbos = new HashMap<>();

    // Viewport
    private int viewportWidth = 800;
    private int viewportHeight = 600;

    public Renderer(RenderDevice device) {
        this(device, new Scene(), SlangCompiler.find());
    }

    public Renderer(RenderDevice device, AbstractScene scene) {
        this(device, scene, SlangCompiler.find());
    }

    public Renderer(RenderDevice device, AbstractScene scene, SlangCompiler slangCompiler) {
        this.device = device;
        this.scene = scene;
        this.meshRenderer = new MeshRenderer();
        this.shaderManager = new ShaderManager(slangCompiler, device);
        this.mvpUbo = device.createBuffer(
                new BufferDescriptor(mat4Layout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));

        // Register built-in material type → shader + struct mappings
        registerMaterialShader(dev.engine.core.material.PbrMaterial.class, "PBR");
        registerMaterialShader(dev.engine.core.material.UnlitMaterial.class, "UNLIT");
        shaderManager.registerMaterialStruct("PBR", dev.engine.core.material.PbrMaterial.ScalarData.class);
        shaderManager.registerMaterialStruct("UNLIT", dev.engine.core.material.UnlitMaterial.ScalarData.class);

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
     * Registers a material type → shader name mapping.
     * The shader name maps to a .slang file via ShaderManager.
     * Built-in: PbrMaterial → "PBR", UnlitMaterial → "UNLIT".
     */
    public void registerMaterialShader(Class<? extends dev.engine.core.material.MaterialData> type, String shaderName) {
        materialShaderMap.put(type, shaderName);
    }

    /**
     * Creates a headless renderer with a stub backend for testing.
     */
    public static Renderer createHeadless() {
        return new Renderer(new HeadlessRenderDevice());
    }

    // --- Scene access ---

    public AbstractScene scene() { return scene; }

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

    /**
     * Creates a mesh from vertex/index data and returns an opaque handle.
     * The handle can be assigned to entities via {@code scene().setMesh(entity, handle)}.
     */
    public Handle<MeshTag> createMesh(float[] vertices, int[] indices, VertexFormat format) {
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

        var vertexInput = device.createVertexInput(format);
        int vertexCount = vertices.length / (format.stride() / Float.BYTES);

        var handle = meshPool.allocate();
        meshRegistry.put(handle.index(), new MeshHandle(vbo, ibo, vertexInput, format, vertexCount, indexCount));
        return handle;
    }

    /**
     * Creates a mesh from MeshData (pure data → GPU upload).
     * Called by Engine.registerMesh().
     */
    public Handle<MeshTag> createMeshFromData(MeshData data) {
        var buf = data.vertexData();
        float[] vertices = new float[buf.remaining() / Float.BYTES];
        buf.mark();
        buf.asFloatBuffer().get(vertices);
        buf.reset();
        return createMesh(vertices, data.indices(), data.format());
    }

    /** @deprecated Use {@code scene().setMesh(entity, meshHandle)} instead */
    @Deprecated
    public void setMesh(Handle<EntityTag> entity, Handle<MeshTag> mesh) {
        scene.setMesh(entity, mesh);
    }

    // --- Material management ---

    /**
     * Creates a material and returns an opaque handle.
     * The handle can be assigned to entities via {@code scene().setMaterial(entity, handle)}.
     */
    public Handle<MaterialTag> createMaterial(MaterialType type) {
        var handle = materialPool.allocate();
        materialRegistry.put(handle.index(), Material.create(type));
        return handle;
    }

    /** Gets the Material object for a material handle (for setting properties). */
    public Material material(Handle<MaterialTag> handle) {
        return materialRegistry.get(handle.index());
    }

    /** @deprecated Use {@code scene().setMaterial(entity, materialHandle)} instead */
    @Deprecated
    public void setMaterial(Handle<EntityTag> entity, Handle<MaterialTag> material) {
        scene.setMaterial(entity, material);
    }

    /**
     * Updates a single material property and emits a transaction.
     */
    public <T> void setMaterialProperty(Handle<EntityTag> entity, dev.engine.core.property.PropertyKey<T> key, T value) {
        scene.setMaterialProperty(entity, key, value);
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
        meshRenderer.processTransactions(SceneAccess.drainTransactions(scene));

        // Resolve mesh/material assignments → renderables
        for (var entity : meshRenderer.getEntities()) {
            if (meshRenderer.getRenderable(entity) != null) continue; // already resolved

            // Try MeshData first (from scene.setMesh(entity, MeshData))
            MeshHandle resolvedMesh = null;
            var data = meshRenderer.getMeshData(entity);
            if (data != null) {
                resolvedMesh = meshDataCache.computeIfAbsent(
                        System.identityHashCode(data),
                        k -> uploadMeshData(data));
            }

            // Fall back to pre-registered handle
            if (resolvedMesh == null) {
                var meshHandle = meshRenderer.getMeshAssignment(entity);
                if (meshHandle != null) resolvedMesh = meshRegistry.get(meshHandle.index());
            }

            if (resolvedMesh == null) continue; // no mesh assigned yet

            // Resolve pipeline from material
            Handle<PipelineResource> pipeline = defaultPipeline;

            // Check typed MaterialData first (PbrMaterial, UnlitMaterial, etc.)
            var typedMat = meshRenderer.getTypedMaterialData(entity);
            if (typedMat != null) {
                var shaderName = materialShaderMap.get(typedMat.getClass());
                if (shaderName != null) {
                    try {
                        pipeline = shaderManager.getPipeline(MaterialType.of(shaderName));
                    } catch (Exception e) { /* fall back to default */ }
                } else if (typedMat instanceof dev.engine.core.material.CustomMaterial cm) {
                    try {
                        pipeline = shaderManager.compileSlangFile(cm.shaderPath());
                    } catch (Exception e) { /* fall back to default */ }
                }
            } else {
                // Legacy Material path
                Material material = meshRenderer.getMaterialData(entity);
                if (material == null) {
                    var matHandle = meshRenderer.getMaterialAssignment(entity);
                    if (matHandle != null) material = materialRegistry.get(matHandle.index());
                }
                if (material != null) {
                    try {
                        if (material.shaderSource() != null) {
                            pipeline = shaderManager.compileSlangFile(material.shaderSource());
                        } else {
                            pipeline = shaderManager.getPipeline(material.type());
                        }
                    } catch (Exception e) { /* fall back to default */ }
                }
            }

            meshRenderer.setRenderable(entity, new Renderable(
                    resolvedMesh.vertexBuffer(), resolvedMesh.indexBuffer(), resolvedMesh.vertexInput(),
                    pipeline, resolvedMesh.vertexCount(), resolvedMesh.indexCount()));
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

                // Upload material data to binding 1
                var typedMaterial = meshRenderer.getTypedMaterialData(cmd.entity());
                if (typedMaterial != null) {
                    uploadTypedMaterialData(typedMaterial, draw);
                } else {
                    uploadMaterialSnapshot(cmd.entity(), cmd.materialData(), draw);
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

    /** Uploads typed MaterialData scalar data as UBO at binding 1. */
    private void uploadTypedMaterialData(dev.engine.core.material.MaterialData matData, CommandRecorder draw) {
        var scalarRecord = matData.scalarData();
        if (scalarRecord == null) return;

        var layout = StructLayout.of(scalarRecord.getClass());
        var key = "typedmat_" + scalarRecord.getClass().getName();
        var ubo = materialUbos.computeIfAbsent(key, k ->
                device.createBuffer(new BufferDescriptor(
                        Math.max(layout.size(), 16), BufferUsage.UNIFORM, AccessPattern.DYNAMIC)));
        try (var w = device.writeBuffer(ubo)) {
            layout.write(w.segment(), 0, scalarRecord);
        }
        draw.bindUniformBuffer(1, ubo);

        // Bind textures via bindless handles (if supported and textures present)
        var textures = matData.textures();
        if (!textures.isEmpty() && device.supports(DeviceCapability.BINDLESS_TEXTURES)) {
            for (var entry : textures.entrySet()) {
                var gpuTex = uploadTexture(entry.getValue());
                long bindlessHandle = device.getBindlessTextureHandle(gpuTex);
                // Bindless handles would be written into an SSBO or the material UBO
                // For now, textures are available via the handle
            }
        }
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

    private MeshHandle uploadMeshData(MeshData data) {
        var buf = data.vertexData();
        float[] vertices = new float[buf.remaining() / Float.BYTES];
        buf.mark();
        buf.asFloatBuffer().get(vertices);
        buf.reset();

        long vbSize = (long) vertices.length * Float.BYTES;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < vertices.length; i++)
                w.segment().setAtIndex(ValueLayout.JAVA_FLOAT, i, vertices[i]);
        }

        Handle<BufferResource> ibo = null;
        if (data.isIndexed()) {
            long ibSize = (long) data.indices().length * Integer.BYTES;
            ibo = device.createBuffer(new BufferDescriptor(ibSize, BufferUsage.INDEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(ibo)) {
                for (int i = 0; i < data.indices().length; i++)
                    w.segment().setAtIndex(ValueLayout.JAVA_INT, i, data.indices()[i]);
            }
        }

        var vertexInput = device.createVertexInput(data.format());
        return new MeshHandle(vbo, ibo, vertexInput, data.format(), data.vertexCount(), data.indexCount());
    }

    /**
     * Uploads TextureData to GPU, caching by identity.
     * Returns a GPU texture handle for binding.
     */
    public Handle<TextureResource> uploadTexture(TextureData data) {
        return textureCache.computeIfAbsent(System.identityHashCode(data), k -> {
            var desc = new dev.engine.graphics.texture.TextureDescriptor(
                    data.width(), data.height(),
                    mapTextureFormat(data.format()));
            var handle = device.createTexture(desc);
            if (!data.compressed()) {
                device.uploadTexture(handle, data.pixels());
            }
            return handle;
        });
    }

    private dev.engine.graphics.texture.TextureFormat mapTextureFormat(TextureData.PixelFormat format) {
        if (format == TextureData.PixelFormat.RGBA8) return dev.engine.graphics.texture.TextureFormat.RGBA8;
        if (format == TextureData.PixelFormat.RGB8) return dev.engine.graphics.texture.TextureFormat.RGB8;
        if (format == TextureData.PixelFormat.R8) return dev.engine.graphics.texture.TextureFormat.R8;
        return dev.engine.graphics.texture.TextureFormat.RGBA8;
    }

    /**
     * Uploads material data from a transaction-driven PropertyMap snapshot.
     * Material data comes through transactions, not by reading Material objects.
     */
    private void uploadMaterialSnapshot(Handle<?> entity, dev.engine.core.property.PropertyMap materialData, CommandRecorder draw) {
        if (materialData == null || materialData.size() == 0) return;

        // Resolve material — prefer transaction-driven Material first, fall back to registry
        Material material = meshRenderer.getMaterialData(entity);
        if (material == null) {
            var matHandle = meshRenderer.getMaterialAssignment(entity);
            material = matHandle != null ? materialRegistry.get(matHandle.index()) : null;
        }
        if (material != null && material.hasRecordData()) {
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
            return;
        }

        // Property-bag path: serialize from the snapshot that came through transactions
        if (material != null) {
            var data = MaterialCompiler.serializeMaterialData(material);
            if (data.remaining() > 0) {
                var key = "mat_" + entity.index();
                var ubo = materialUbos.computeIfAbsent(key, k ->
                        device.createBuffer(new dev.engine.graphics.buffer.BufferDescriptor(
                                Math.max(data.remaining(), 16), BufferUsage.UNIFORM, AccessPattern.DYNAMIC)));
                try (var w = device.writeBuffer(ubo)) {
                    for (int i = 0; i < data.remaining(); i++) {
                        w.segment().set(java.lang.foreign.ValueLayout.JAVA_BYTE, i, data.get(i));
                    }
                }
                draw.bindUniformBuffer(1, ubo);
            }
        }
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
