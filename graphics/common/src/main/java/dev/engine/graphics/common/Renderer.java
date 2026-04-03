package dev.engine.graphics.common;

import dev.engine.core.gpu.BufferWriter;
import dev.engine.core.handle.Handle;
import dev.engine.core.layout.LayoutMode;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.core.handle.HandlePool;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;
import dev.engine.core.property.MutablePropertyMap;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.core.shader.GlobalParamsRegistry;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.core.shader.params.CameraParams;
import dev.engine.core.shader.params.EngineParams;
import dev.engine.core.shader.params.ObjectParams;
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
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.core.asset.TextureData;
import dev.engine.core.mesh.MeshData;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.renderer.DrawCommand;
import dev.engine.graphics.renderer.MeshRenderer;
import dev.engine.graphics.renderer.Renderable;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.core.mesh.VertexFormat;

import dev.engine.core.memory.NativeMemory;
import dev.engine.graphics.shader.ShaderCompiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final GlobalParamsRegistry globalParams;

    // GPU buffers for each registered global param block
    private final Map<String, Handle<BufferResource>> globalUbos = new HashMap<>();
    private final Map<String, StructLayout> globalLayouts = new HashMap<>();
    private Handle<PipelineResource> defaultPipeline;

    // Engine timing
    private float engineTime = 0f;
    private float lastDeltaTime = 0f;
    private int frameCount = 0;

    // Mesh registry: Handle<MeshTag> → MeshHandle (GPU resources)
    private final HandlePool<MeshTag> meshPool = new HandlePool<>();
    private final Map<Integer, MeshHandle> meshRegistry = new HashMap<>();

    // Auto-upload cache: MeshData identity hash → MeshHandle (GPU resources)
    private final Map<Integer, MeshHandle> meshDataCache = new HashMap<>();

    // Texture auto-upload cache: TextureData identity hash → GPU texture handle
    private final Map<Integer, Handle<TextureResource>> textureCache = new HashMap<>();

    // Material data UBO — lazily created per entity
    private final Map<String, Handle<BufferResource>> materialUbos = new HashMap<>();
    // Per-entity object params UBO (world matrix)
    private final Map<String, Handle<BufferResource>> objectUbos = new HashMap<>();

    // Shader hint → compiled shader (pipeline + reflection bindings)
    private final Map<String, CompiledShader> shaderCache = new HashMap<>();

    // Per-entity compiled shader reference (for texture binding during draw)
    private final Map<Integer, CompiledShader> entityShaders = new HashMap<>();

    // Default sampler for texture bindings (lazily created)
    private Handle<SamplerResource> defaultSampler;

    // Three-layer render state: forced > material > defaults
    private final MutablePropertyMap defaultProperties = new MutablePropertyMap();
    private final MutablePropertyMap forcedProperties = new MutablePropertyMap();

    // Viewport
    private int viewportWidth = 800;
    private int viewportHeight = 600;


    public Renderer(RenderDevice device, ShaderCompiler compiler) {
        this(device, new Scene(), compiler);
    }

    public Renderer(RenderDevice device, AbstractScene scene, ShaderCompiler compiler) {
        this.device = device;
        this.scene = scene;
        this.meshRenderer = new MeshRenderer();

        // Register default global params
        this.globalParams = new GlobalParamsRegistry();
        globalParams.register("Engine", EngineParams.class, 0);
        globalParams.register("Camera", CameraParams.class, 1);
        globalParams.register("Object", ObjectParams.class, 2);

        this.shaderManager = new ShaderManager(device, globalParams, compiler);

        // Create GPU buffers for each registered global using STD140 layout
        // (UBOs require std140 alignment for cross-backend compatibility)
        for (var entry : globalParams.entries()) {
            var layout = StructLayout.of(entry.recordType(), LayoutMode.STD140);
            globalLayouts.put(entry.name(), layout);
            globalUbos.put(entry.name(), device.createBuffer(
                    new BufferDescriptor(layout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC)));
        }

        // Pipelines are compiled lazily when first entity with a material is seen,
        // because shader compilation requires material keys for param block generation.

        // Initialize render state defaults
        PropertyMap defaults = RenderState.defaults();
        for (var key : defaults.keys()) {
            @SuppressWarnings("unchecked")
            var typedKey = (PropertyKey<Object>) key;
            defaultProperties.set(typedKey, defaults.get(key));
        }
    }

    /**
     * Creates a headless renderer with a stub backend for testing.
     */
    public static Renderer createHeadless() {
        return new Renderer(new HeadlessRenderDevice(), new NoOpShaderCompiler());
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
                w.memory().putFloat((long) i * Float.BYTES, vertices[i]);
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
                    w.memory().putInt((long) i * Integer.BYTES, indices[i]);
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

    /** @deprecated Use {@code scene().setMesh(entity, MeshData)} instead */
    @Deprecated
    public void setMesh(Handle<EntityTag> entity, Handle<MeshTag> mesh) {
        scene.setMesh(entity, mesh);
    }

    // --- Material management ---

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

    // --- Global params ---

    /**
     * Registers a user-defined global param block.
     * The block becomes available as a Slang global in all subsequently compiled shaders.
     * Must be called before shaders that use it are compiled.
     *
     * @param name       the block name (e.g., "Light") — shader uses {@code light.direction()}
     * @param recordType the Java record defining the fields
     * @param binding    the fixed UBO binding index
     */
    public <T extends Record> void registerGlobalParams(String name, Class<T> recordType, int binding) {
        globalParams.register(name, recordType, binding);
        var layout = StructLayout.of(recordType);
        globalLayouts.put(name, layout);
        globalUbos.put(name, device.createBuffer(
                new BufferDescriptor(layout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC)));
        // Invalidate shader cache since new global changes generated preamble
        shaderManager.invalidateAll();
    }

    /**
     * Registers a user-defined global param block with an auto-assigned binding.
     */
    public <T extends Record> void registerGlobalParams(String name, Class<T> recordType) {
        registerGlobalParams(name, recordType, globalParams.nextBinding());
    }

    /**
     * Updates per-frame data for a registered global param block.
     */
    public void updateGlobalParams(String name, Object data) {
        globalParams.update(name, data);
    }

    /** Returns the global params registry. */
    public GlobalParamsRegistry globalParams() { return globalParams; }

    // --- Viewport ---

    public void setViewport(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    // --- Render state property resolution ---

    public <T> void setDefault(PropertyKey<T> key, T value) {
        defaultProperties.set(key, value);
    }

    public <T> void forceProperty(PropertyKey<T> key, T value) {
        forcedProperties.set(key, value);
    }

    public <T> void clearForced(PropertyKey<T> key) {
        forcedProperties.remove(key);
    }

    /** Updates engine timing. Called by BaseApplication each frame. */
    public void updateTime(float time, float deltaTime) {
        this.engineTime = time;
        this.lastDeltaTime = deltaTime;
    }

    // --- Render ---

    public void renderFrame() {
        // Process scene transactions
        var transactions = SceneAccess.drainTransactions(scene);
        if (frameCount <= 3) {
            System.out.println("[Renderer] renderFrame: " + transactions.size() + " txns, " +
                meshRenderer.getEntities().size() + " entities before");
        }
        meshRenderer.processTransactions(transactions);
        if (frameCount <= 3) {
            System.out.println("[Renderer] renderFrame: " + meshRenderer.getEntities().size() + " entities after");
        }

        // Resolve mesh/material assignments → renderables
        for (var entity : meshRenderer.getEntities()) {
            if (meshRenderer.getRenderable(entity) != null) continue; // already resolved

            if (frameCount <= 3) {
                var md = meshRenderer.getMeshData(entity);
                var mat = meshRenderer.getMaterialData(entity);
                System.out.println("[Renderer] Entity " + entity + ": meshData=" + (md != null) +
                    ", material=" + (mat != null ? mat.shaderHint() : "null"));
            }

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

            // Resolve shader from MaterialData.shaderHint() + material keys
            // Filter out render state keys — they control pipeline state, not shader uniforms.
            CompiledShader compiled = null;
            var mat = meshRenderer.getMaterialData(entity);
            if (mat != null && mat.shaderHint() != null) {
                var hint = mat.shaderHint();
                var shaderKeys = mat.keys().stream()
                        .filter(k -> !isRenderStateKey(k))
                        .collect(java.util.stream.Collectors.toSet());
                var keyNames = shaderKeys.stream()
                        .map(dev.engine.core.property.PropertyKey::name)
                        .sorted()
                        .toList();
                var cacheKey = hint + "_" + String.join("_", keyNames);
                compiled = shaderCache.computeIfAbsent(cacheKey, k -> {
                    try {
                        if (hint.contains("/") || hint.contains(".")) {
                            return shaderManager.compileSlangSource(
                                    loadShaderFile(hint), hint);
                        }
                        return shaderManager.getShaderWithMaterial(hint, shaderKeys);
                    } catch (Exception e) {
                        System.err.println("[Renderer] Shader compilation FAILED for hint='" + hint + "': " + e);
                        e.printStackTrace();
                        return null;
                    }
                });
            }

            var pipeline = compiled != null ? compiled.pipeline() : defaultPipeline;
            var bindings = compiled != null ? extractBufferBindings(compiled) : Map.<String, Integer>of();

            if (compiled != null) {
                entityShaders.put(entity.index(), compiled);
            }

            meshRenderer.setRenderable(entity, new Renderable(
                    resolvedMesh.vertexBuffer(), resolvedMesh.indexBuffer(), resolvedMesh.vertexInput(),
                    pipeline, resolvedMesh.vertexCount(), resolvedMesh.indexCount(), bindings));
        }

        device.beginFrame();
        frameCount++;

        // Setup pass
        var setup = new CommandRecorder();
        setup.viewport(0, 0, viewportWidth, viewportHeight);
        setup.setRenderState(RenderState.defaults());
        setup.clear(0.05f, 0.05f, 0.08f, 1f);
        if (defaultPipeline != null) {
            setup.bindPipeline(defaultPipeline);
        }
        device.submit(setup.finish());

        // Update engine params (once per frame)
        globalParams.update("Engine", new EngineParams(engineTime, lastDeltaTime,
                new Vec2(viewportWidth, viewportHeight), frameCount));

        // Upload camera params (once per camera, pure camera data)
        if (activeCamera != null) {
            globalParams.update("Camera", new CameraParams(
                    activeCamera.viewProjectionMatrix(),
                    activeCamera.viewMatrix(),
                    activeCamera.projectionMatrix(),
                    activeCamera.position(),
                    activeCamera.nearPlane(), activeCamera.farPlane()));
        }

        // Upload all per-frame global params to GPU (skip per-object ones)
        for (var entry : globalParams.entries()) {
            if (entry.data() == null) continue;
            if ("Object".equals(entry.name())) continue; // per-draw, see below
            var ubo = globalUbos.get(entry.name());
            var layout = globalLayouts.get(entry.name());
            if (ubo != null && layout != null) {
                try (var w = device.writeBuffer(ubo)) {
                    layout.write(w.memory(), 0, entry.data());
                }
            }
        }

        // Draw each object
        if (activeCamera != null) {
            for (var cmd : meshRenderer.collectBatch()) {
                // Skip if no pipeline
                if (cmd.renderable().pipeline() == null) continue;

                // Per-object: upload object params to a per-entity UBO
                var objectLayout = globalLayouts.get("Object");
                Handle<BufferResource> objectUbo = null;
                if (objectLayout != null) {
                    var objectKey = "obj_" + cmd.entity().index();
                    objectUbo = objectUbos.computeIfAbsent(objectKey, k ->
                            device.createBuffer(new BufferDescriptor(
                                    objectLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC)));
                    var objectParams = new ObjectParams(cmd.transform());
                    try (var w = device.writeBuffer(objectUbo)) {
                        objectLayout.write(w.memory(), 0, objectParams);
                    }
                }

                var draw = new CommandRecorder();
                draw.bindPipeline(cmd.renderable().pipeline());

                // Resolve and apply render state per entity
                var mat = meshRenderer.getMaterialData(cmd.entity());
                var renderState = resolveRenderState(mat);
                draw.setRenderState(renderState);

                // Bind all registered global params by name
                var r = cmd.renderable();
                for (var entry : globalParams.entries()) {
                    Handle<BufferResource> ubo;
                    if ("Object".equals(entry.name())) {
                        ubo = objectUbo; // per-entity
                    } else {
                        ubo = globalUbos.get(entry.name());
                    }
                    if (ubo != null) {
                        draw.bindUniformBuffer(
                                r.bindingFor(entry.name() + "Buffer", entry.binding()), ubo);
                    }
                }

                // Upload material data (mat already fetched above for render state)
                if (mat != null) {
                    int materialSlot = r.bindingFor("MaterialBuffer", globalParams.nextBinding());
                    uploadMaterialData(mat, cmd.entity(), draw, materialSlot);

                    // Bind textures from material
                    var compiled = entityShaders.get(cmd.entity().index());
                    if (compiled != null) {
                        bindMaterialTextures(mat, compiled, draw);
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

    @SuppressWarnings("unchecked")
    private PropertyMap resolveRenderState(MaterialData material) {
        var builder = PropertyMap.builder();
        // Layer 1: defaults
        for (var key : defaultProperties.keys()) {
            builder.set((PropertyKey<Object>) key, defaultProperties.get(key));
        }
        // Layer 2: material overrides (render state keys in MaterialData)
        if (material != null) {
            for (var key : material.keys()) {
                Object value = material.get(key);
                if (value != null && isRenderStateKey(key)) {
                    builder.set((PropertyKey<Object>) key, value);
                }
            }
        }
        // Layer 3: forced overrides
        for (var key : forcedProperties.keys()) {
            builder.set((PropertyKey<Object>) key, forcedProperties.get(key));
        }
        return builder.build();
    }

    private boolean isRenderStateKey(PropertyKey<?> key) {
        return key == RenderState.DEPTH_TEST || key == RenderState.DEPTH_WRITE
            || key == RenderState.DEPTH_FUNC || key == RenderState.BLEND_MODE
            || key == RenderState.CULL_MODE || key == RenderState.FRONT_FACE
            || key == RenderState.WIREFRAME || key == RenderState.LINE_WIDTH
            || key == RenderState.SCISSOR_TEST
            || key == RenderState.STENCIL_TEST || key == RenderState.STENCIL_FUNC
            || key == RenderState.STENCIL_REF || key == RenderState.STENCIL_MASK
            || key == RenderState.STENCIL_FAIL || key == RenderState.STENCIL_DEPTH_FAIL
            || key == RenderState.STENCIL_PASS;
    }

    /**
     * Uploads MaterialData properties as UBO at the given binding slot.
     * Uses BufferWriter for all value serialization.
     */
    private void uploadMaterialData(MaterialData matData, Handle<?> entity, CommandRecorder draw, int bindingSlot) {
        var keys = matData.keys();
        if (keys.isEmpty()) return;

        // Filter to BufferWriter-supported keys, excluding render state keys (they
        // control pipeline state, not shader uniforms), and sort for deterministic layout
        var scalarKeys = keys.stream()
                .filter(k -> BufferWriter.supports(k.type()) && !isRenderStateKey(k))
                .sorted(Comparator.comparing(PropertyKey::name))
                .toList();

        if (scalarKeys.isEmpty()) return;

        // Calculate total size with std140-style alignment
        int totalSize = 0;
        int maxAlign = 4; // minimum alignment for UBOs
        for (var key : scalarKeys) {
            if (key.type() == Vec3.class) {
                totalSize = align(totalSize, 16);
                totalSize += 16; // Vec3 padded to 16 bytes in std140
                maxAlign = Math.max(maxAlign, 16);
            } else if (key.type() == Vec4.class || key.type() == Mat4.class) {
                totalSize = align(totalSize, 16);
                totalSize += BufferWriter.sizeOf(key.type());
                maxAlign = Math.max(maxAlign, 16);
            } else if (key.type() == Vec2.class) {
                totalSize = align(totalSize, 8);
                totalSize += BufferWriter.sizeOf(key.type());
                maxAlign = Math.max(maxAlign, 8);
            } else {
                totalSize += BufferWriter.sizeOf(key.type());
            }
        }
        // Round up to struct alignment (std140 requires struct size to be multiple of max alignment)
        totalSize = align(totalSize, maxAlign);

        final int uboSize = Math.max(totalSize, 16);
        var uboKey = "mat_" + entity.index();
        var ubo = materialUbos.computeIfAbsent(uboKey, k ->
                device.createBuffer(new BufferDescriptor(
                        uboSize, BufferUsage.UNIFORM, AccessPattern.DYNAMIC)));

        try (var w = device.writeBuffer(ubo)) {
            int offset = 0;
            for (var key : scalarKeys) {
                var value = matData.get(key);
                if (value == null) continue;

                if (key.type() == Vec3.class) {
                    offset = align(offset, 16);
                    BufferWriter.write(w.memory(), offset, value);
                    offset += 16; // std140 padded
                } else {
                    BufferWriter.write(w.memory(), offset, value);
                    offset += BufferWriter.sizeOf(key.type());
                }
            }
        }

        draw.bindUniformBuffer(bindingSlot, ubo);
    }

    /**
     * Binds TextureData properties from MaterialData to the correct texture units.
     *
     * <p>Texture bindings are resolved from shader reflection metadata.
     * The order must match how {@link ShaderManager#prependParamBlocks} generates texture declarations.
     */
    private void bindMaterialTextures(MaterialData matData, CompiledShader shader, CommandRecorder draw) {
        // Sort texture keys for deterministic binding order (must match ShaderManager.prependParamBlocks)
        var sortedTexKeys = matData.keys().stream()
                .filter(k -> k.type() == TextureData.class)
                .sorted(java.util.Comparator.comparing(PropertyKey::name))
                .toList();

        int texUnit = 0;
        for (var key : sortedTexKeys) {
            TextureData texData = (TextureData) matData.get(key);
            if (texData == null) {
                texUnit++;
                continue;
            }

            var texHandle = uploadTexture(texData);

            // Try to resolve binding from shader reflection (GLSL-parsed or Slang reflection)
            String texParamName = mapTextureKeyToShaderParam(key);
            var texBinding = shader.findBinding(texParamName);
            int unit = texBinding != null ? texBinding.binding() : texUnit;

            draw.bindTexture(unit, texHandle);
            draw.bindSampler(unit, getOrCreateDefaultSampler());
            texUnit++;
        }
    }

    private String mapTextureKeyToShaderParam(PropertyKey<?> key) {
        return switch (key.name()) {
            case "albedoMap" -> "albedoTexture";
            case "normalMap" -> "normalTexture";
            case "roughnessMap" -> "roughnessTexture";
            case "metallicMap" -> "metallicTexture";
            case "emissiveMap" -> "emissiveTexture";
            case "aoMap" -> "aoTexture";
            default -> key.name();
        };
    }

    private Handle<SamplerResource> getOrCreateDefaultSampler() {
        if (defaultSampler == null) {
            defaultSampler = device.createSampler(SamplerDescriptor.linear());
        }
        return defaultSampler;
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
                w.memory().putFloat((long) i * Float.BYTES, vertices[i]);
        }

        Handle<BufferResource> ibo = null;
        if (data.isIndexed()) {
            long ibSize = (long) data.indices().length * Integer.BYTES;
            ibo = device.createBuffer(new BufferDescriptor(ibSize, BufferUsage.INDEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(ibo)) {
                for (int i = 0; i < data.indices().length; i++)
                    w.memory().putInt((long) i * Integer.BYTES, data.indices()[i]);
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

    private static int align(int offset, int alignment) {
        return (offset + alignment - 1) & ~(alignment - 1);
    }

    /**
     * Extracts buffer name → binding slot map from a CompiledShader's reflection.
     * Only includes constant buffer bindings.
     */
    private Map<String, Integer> extractBufferBindings(CompiledShader compiled) {
        if (compiled.bindings().isEmpty()) return Map.of();
        var result = new HashMap<String, Integer>();
        for (var entry : compiled.bindings().entrySet()) {
            var binding = entry.getValue();
            if (binding.type() == CompiledShader.BindingType.CONSTANT_BUFFER) {
                result.put(entry.getKey(), binding.binding());
            }
        }
        return result;
    }

    private String loadShaderFile(String path) {
        // Try classpath first (works on all platforms including TeaVM)
        try (var is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ignored) {}
        // Fallback to filesystem (desktop only, loaded dynamically to avoid TeaVM tracing)
        try {
            var filesClass = Class.forName("java.nio.file.Files");
            var pathClass = Class.forName("java.nio.file.Path");
            var ofMethod = pathClass.getMethod("of", String.class, String[].class);
            var readMethod = filesClass.getMethod("readString", pathClass);
            var pathObj = ofMethod.invoke(null, path, new String[0]);
            return (String) readMethod.invoke(null, pathObj);
        } catch (Exception ignored) {
            return null;
        }
    }

    // --- Low-level access (escape hatch) ---

    public RenderDevice device() { return device; }

    @Override
    public void close() {
        for (var ubo : globalUbos.values()) device.destroyBuffer(ubo);
        globalUbos.clear();
        for (var ubo : materialUbos.values()) device.destroyBuffer(ubo);
        materialUbos.clear();
        for (var ubo : objectUbos.values()) device.destroyBuffer(ubo);
        objectUbos.clear();
        if (defaultSampler != null) device.destroySampler(defaultSampler);
        if (defaultPipeline != null) device.destroyPipeline(defaultPipeline);
        device.close();
    }
}
