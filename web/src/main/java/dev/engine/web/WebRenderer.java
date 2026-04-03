package dev.engine.web;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.webgpu.WgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmSlangCompiler;
import org.teavm.jso.JSBody;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight renderer for the web target that uses engine math/scene types
 * but drives WebGPU directly through {@link WgpuBindings}.
 *
 * <p>This bypasses the desktop {@code Renderer} / {@code ShaderManager} pipeline
 * (which depends on FFM-based Slang and reflection-based StructLayout) and instead
 * compiles shaders via the Slang WASM compiler and manages GPU resources directly.
 *
 * <p>Uses engine types: {@link Mat4}, {@link Vec3}, {@link Transform}, {@link MaterialData}.
 */
public class WebRenderer {

    // ── Slang shader source ──────────────────────────────────────────────
    // Self-contained unlit shader (no imports). Uses ParameterBlock<T> for
    // each uniform block, which Slang maps to @group(N) @binding(0) in WGSL.
    //
    // ParameterBlock ordering determines group indices:
    //   camera   -> @group(0) @binding(0)
    //   object   -> @group(1) @binding(0)
    //   material -> @group(2) @binding(0)

    private static final String UNLIT_SLANG = """
            struct CameraData {
                float4x4 viewProjection;
            };
            struct ObjectData {
                float4x4 world;
            };
            struct MaterialData {
                float3 color;
            };

            ParameterBlock<CameraData> camera;
            ParameterBlock<ObjectData> object;
            ParameterBlock<MaterialData> material;

            struct VertexInput {
                float3 position : POSITION;
                float3 normal   : NORMAL;
                float2 uv       : TEXCOORD;
            };

            struct VertexOutput {
                float4 position : SV_Position;
                float3 normal;
                float2 uv;
            };

            [shader("vertex")]
            VertexOutput vertexMain(VertexInput input) {
                VertexOutput output;
                float4x4 mvp = mul(object.world, camera.viewProjection);
                output.position = mul(float4(input.position, 1.0), mvp);
                output.normal = mul(float4(input.normal, 0.0), object.world).xyz;
                output.uv = input.uv;
                return output;
            }

            [shader("fragment")]
            float4 fragmentMain(VertexOutput input) : SV_Target {
                float3 N = normalize(input.normal);
                float3 L = normalize(float3(0.5, 1.0, 0.3));
                float NdotL = max(dot(N, L), 0.0);
                float3 lit = material.color * (0.3 + 0.7 * NdotL);
                return float4(lit, 1.0);
            }
            """;

    // Alternative Slang source using cbuffer (fallback if ParameterBlock doesn't work
    // with WASM Slang). cbuffer maps to @group(0) with sequential @binding(N) in WGSL.
    @SuppressWarnings("unused")
    private static final String UNLIT_SLANG_CBUFFER = """
            cbuffer CameraBuffer : register(b0, space0) {
                float4x4 viewProjection;
            };
            cbuffer ObjectBuffer : register(b0, space1) {
                float4x4 world;
            };
            cbuffer MaterialBuffer : register(b0, space2) {
                float3 color;
            };

            struct VertexInput {
                float3 position : POSITION;
                float3 normal   : NORMAL;
                float2 uv       : TEXCOORD;
            };

            struct VertexOutput {
                float4 position : SV_Position;
                float3 normal;
                float2 uv;
            };

            [shader("vertex")]
            VertexOutput vertexMain(VertexInput input) {
                VertexOutput output;
                float4x4 mvp = mul(world, viewProjection);
                output.position = mul(float4(input.position, 1.0), mvp);
                output.normal = mul(float4(input.normal, 0.0), world).xyz;
                output.uv = input.uv;
                return output;
            }

            [shader("fragment")]
            float4 fragmentMain(VertexOutput input) : SV_Target {
                float3 N = normalize(input.normal);
                float3 L = normalize(float3(0.5, 1.0, 0.3));
                float NdotL = max(dot(N, L), 0.0);
                float3 lit = color * (0.3 + 0.7 * NdotL);
                return float4(lit, 1.0);
            }
            """;

    // Fallback WGSL if Slang WASM is not available
    private static final String FALLBACK_VERTEX_WGSL = """
            struct CameraData {
                viewProjection: mat4x4f,
            }
            struct ObjectData {
                world: mat4x4f,
            }
            @group(0) @binding(0) var<uniform> camera: CameraData;
            @group(1) @binding(0) var<uniform> object_: ObjectData;

            struct VertexInput {
                @location(0) position: vec3f,
                @location(1) normal: vec3f,
                @location(2) uv: vec2f,
            }
            struct VertexOutput {
                @builtin(position) position: vec4f,
                @location(0) normal: vec3f,
                @location(1) uv: vec2f,
            }

            @vertex
            fn vertexMain(input: VertexInput) -> VertexOutput {
                var output: VertexOutput;
                let mvp = object_.world * camera.viewProjection;
                output.position = vec4f(input.position, 1.0) * mvp;
                output.normal = (vec4f(input.normal, 0.0) * object_.world).xyz;
                output.uv = input.uv;
                return output;
            }
            """;

    private static final String FALLBACK_FRAGMENT_WGSL = """
            struct MaterialData {
                color: vec3f,
            }
            @group(2) @binding(0) var<uniform> material: MaterialData;

            struct VertexOutput {
                @builtin(position) position: vec4f,
                @location(0) normal: vec3f,
                @location(1) uv: vec2f,
            }

            @fragment
            fn fragmentMain(input: VertexOutput) -> @location(0) vec4f {
                let N = normalize(input.normal);
                let L = normalize(vec3f(0.5, 1.0, 0.3));
                let NdotL = max(dot(N, L), 0.0);
                let lit = material.color * (0.3 + 0.7 * NdotL);
                return vec4f(lit, 1.0);
            }
            """;

    // ── Cube mesh data (matches PrimitiveMeshes.cube()) ──────────────────
    // 24 vertices: 4 per face with correct normals
    // Stride: 3 pos + 3 normal + 2 uv = 8 floats = 32 bytes

    private static final int VERTEX_STRIDE = 32; // bytes
    private static final int VERTEX_COMPONENTS = 8; // floats per vertex

    // ── Scene entity ─────────────────────────────────────────────────────

    private record SceneEntity(Transform transform, Vec3 color) {}

    // ── State ────────────────────────────────────────────────────────────

    private final WgpuBindings gpu;
    private final int deviceId;
    private final int queueId;

    // Compiled pipeline
    private long pipeline;
    private long bindGroupLayout0; // camera
    private long bindGroupLayout1; // object
    private long bindGroupLayout2; // material
    private long pipelineLayout;

    // Depth texture
    private long depthTexture;
    private long depthTextureView;
    private int depthWidth;
    private int depthHeight;

    // Cube mesh GPU resources
    private long vertexBuffer;
    private int vertexBufferSize;
    private long indexBuffer;
    private int indexBufferSize;
    private int indexCount;

    // Per-entity UBOs (reused across frames)
    private long cameraUbo;   // 64 bytes: mat4x4
    private long objectUbo;   // 64 bytes: mat4x4
    private long materialUbo; // 16 bytes: vec3 + padding

    // Camera
    private Mat4 viewMatrix = Mat4.IDENTITY;
    private Mat4 projectionMatrix = Mat4.IDENTITY;

    // Entities to render
    private final List<SceneEntity> entities = new ArrayList<>();

    private boolean slangCompiled;

    public WebRenderer(WgpuBindings gpu, int deviceId, int queueId) {
        this.gpu = gpu;
        this.deviceId = deviceId;
        this.queueId = queueId;
    }

    /**
     * Initializes the renderer: compiles shaders, creates pipeline, uploads mesh.
     *
     * @param colorFormat the canvas color format (e.g., BGRA8_UNORM)
     * @param width       canvas width
     * @param height      canvas height
     */
    public void init(int colorFormat, int width, int height) {
        // 1. Compile shaders
        String vertexWGSL;
        String fragmentWGSL;

        if (TeaVmSlangCompiler.isAvailable()) {
            consoleLog("[WebRenderer] Slang WASM available, version: "
                    + TeaVmSlangCompiler.getVersionString());
            // Try ParameterBlock variant first, then cbuffer variant
            String[] wgsl = tryCompileSlang(UNLIT_SLANG, "ParameterBlock");
            if (wgsl == null) {
                wgsl = tryCompileSlang(UNLIT_SLANG_CBUFFER, "cbuffer");
            }
            if (wgsl != null) {
                vertexWGSL = wgsl[0];
                fragmentWGSL = wgsl[1];
                slangCompiled = true;
            } else {
                consoleLog("[WebRenderer] All Slang variants failed, using fallback WGSL");
                vertexWGSL = FALLBACK_VERTEX_WGSL;
                fragmentWGSL = FALLBACK_FRAGMENT_WGSL;
            }
        } else {
            consoleLog("[WebRenderer] Slang WASM not available, using fallback WGSL");
            vertexWGSL = FALLBACK_VERTEX_WGSL;
            fragmentWGSL = FALLBACK_FRAGMENT_WGSL;
        }

        long vertexModule = gpu.deviceCreateShaderModule(deviceId, vertexWGSL);
        long fragmentModule = gpu.deviceCreateShaderModule(deviceId, fragmentWGSL);

        // 2. Create bind group layouts
        //    group 0: camera UBO (binding 0, vertex-visible)
        //    group 1: object UBO (binding 0, vertex-visible)
        //    group 2: material UBO (binding 0, fragment-visible)
        int vertVis = WgpuBindings.SHADER_STAGE_VERTEX;
        int fragVis = WgpuBindings.SHADER_STAGE_FRAGMENT;

        bindGroupLayout0 = gpu.deviceCreateBindGroupLayout(deviceId,
                new WgpuBindings.BindGroupLayoutEntry[]{
                        new WgpuBindings.BindGroupLayoutEntry(0, vertVis,
                                WgpuBindings.BindingType.UNIFORM_BUFFER)
                });
        bindGroupLayout1 = gpu.deviceCreateBindGroupLayout(deviceId,
                new WgpuBindings.BindGroupLayoutEntry[]{
                        new WgpuBindings.BindGroupLayoutEntry(0, vertVis,
                                WgpuBindings.BindingType.UNIFORM_BUFFER)
                });
        bindGroupLayout2 = gpu.deviceCreateBindGroupLayout(deviceId,
                new WgpuBindings.BindGroupLayoutEntry[]{
                        new WgpuBindings.BindGroupLayoutEntry(0, fragVis,
                                WgpuBindings.BindingType.UNIFORM_BUFFER)
                });

        // 3. Create pipeline layout
        pipelineLayout = gpu.deviceCreatePipelineLayout(deviceId,
                new long[]{bindGroupLayout0, bindGroupLayout1, bindGroupLayout2});

        // 4. Create depth texture
        createDepthResources(width, height);

        // 5. Create render pipeline
        var pipelineDesc = new WgpuBindings.RenderPipelineDescriptor(
                pipelineLayout,
                vertexModule, "vertexMain",
                fragmentModule, "fragmentMain",
                new WgpuBindings.VertexBufferLayoutDesc(
                        VERTEX_STRIDE,
                        WgpuBindings.VERTEX_STEP_MODE_VERTEX,
                        new WgpuBindings.VertexAttributeDesc[]{
                                // position: float32x3 at offset 0, location 0
                                new WgpuBindings.VertexAttributeDesc(
                                        WgpuBindings.VERTEX_FORMAT_FLOAT32X3, 0, 0),
                                // normal: float32x3 at offset 12, location 1
                                new WgpuBindings.VertexAttributeDesc(
                                        WgpuBindings.VERTEX_FORMAT_FLOAT32X3, 12, 1),
                                // uv: float32x2 at offset 24, location 2
                                new WgpuBindings.VertexAttributeDesc(
                                        WgpuBindings.VERTEX_FORMAT_FLOAT32X2, 24, 2),
                        }
                ),
                WgpuBindings.PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                WgpuBindings.FRONT_FACE_CCW,
                WgpuBindings.CULL_MODE_BACK,
                // Depth/stencil
                WgpuBindings.TEXTURE_FORMAT_DEPTH24_PLUS,
                WgpuBindings.OPTIONAL_BOOL_TRUE, // depth write enabled
                WgpuBindings.COMPARE_LESS,        // depth compare
                0, 0,                              // stencil masks (unused)
                null, null,                        // stencil face states
                // Color target
                colorFormat,
                WgpuBindings.BLEND_FACTOR_ONE,
                WgpuBindings.BLEND_FACTOR_ZERO,
                WgpuBindings.BLEND_OP_ADD,
                WgpuBindings.BLEND_FACTOR_ONE,
                WgpuBindings.BLEND_FACTOR_ZERO,
                WgpuBindings.BLEND_OP_ADD
        );
        pipeline = gpu.deviceCreateRenderPipeline(deviceId, pipelineDesc);

        // 6. Create cube mesh
        createCubeMesh();

        // 7. Create UBOs
        int uniformUsage = WgpuBindings.BUFFER_USAGE_UNIFORM | WgpuBindings.BUFFER_USAGE_COPY_DST;
        cameraUbo = gpu.deviceCreateBuffer(deviceId, 64, uniformUsage);
        objectUbo = gpu.deviceCreateBuffer(deviceId, 64, uniformUsage);
        // Material UBO: vec3 color = 12 bytes, but WebGPU requires min binding size
        // to be 16-byte aligned for uniform buffers
        materialUbo = gpu.deviceCreateBuffer(deviceId, 16, uniformUsage);
    }

    /**
     * Sets up the camera. Matches the TWO_CUBES_UNLIT test scene.
     */
    public void setupCamera(int width, int height) {
        projectionMatrix = Mat4.perspective(
                (float) Math.toRadians(60), (float) width / height, 0.1f, 100f);
        viewMatrix = Mat4.lookAt(
                new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
    }

    /**
     * Adds an entity to render (a cube with the given transform and material).
     */
    public void addEntity(Transform transform, MaterialData material) {
        Vec3 color = material.get(MaterialData.COLOR);
        if (color == null) {
            color = material.get(MaterialData.ALBEDO_COLOR);
        }
        if (color == null) {
            color = new Vec3(1, 1, 1);
        }
        entities.add(new SceneEntity(transform, color));
    }

    /**
     * Returns whether Slang WASM compilation was used.
     */
    public boolean isSlangCompiled() {
        return slangCompiled;
    }

    /**
     * Renders one frame to the given canvas texture view.
     */
    public void renderFrame(int colorTextureViewId, int width, int height) {
        // Recreate depth resources if canvas size changed
        if (width != depthWidth || height != depthHeight) {
            destroyDepthResources();
            createDepthResources(width, height);
            // Update projection for new aspect ratio
            projectionMatrix = Mat4.perspective(
                    (float) Math.toRadians(60), (float) width / height, 0.1f, 100f);
        }

        // Upload camera UBO: viewProjection matrix
        Mat4 vp = projectionMatrix.mul(viewMatrix);
        uploadMatrix(cameraUbo, vp);

        // Create command encoder
        long encoder = gpu.deviceCreateCommandEncoder(deviceId);

        // Begin render pass with color + depth attachments
        var colorAtt = new WgpuBindings.ColorAttachment(
                colorTextureViewId, 0.05f, 0.05f, 0.08f, 1.0f);
        var depthAtt = new WgpuBindings.DepthStencilAttachment(depthTextureView, 1.0f, 0);
        var passDesc = new WgpuBindings.RenderPassDescriptor(
                new WgpuBindings.ColorAttachment[]{colorAtt}, depthAtt);
        long pass = gpu.commandEncoderBeginRenderPass(encoder, passDesc);

        // Set pipeline and mesh
        gpu.renderPassSetPipeline(pass, pipeline);
        gpu.renderPassSetVertexBuffer(pass, 0, vertexBuffer, 0, vertexBufferSize);
        gpu.renderPassSetIndexBuffer(pass, indexBuffer,
                WgpuBindings.INDEX_FORMAT_UINT32, 0, indexBufferSize);

        // Create camera bind group (shared across all draws)
        long cameraBg = gpu.deviceCreateBindGroup(deviceId, bindGroupLayout0,
                new WgpuBindings.BindGroupEntry[]{
                        new WgpuBindings.BindGroupEntry(0,
                                WgpuBindings.BindingResourceType.BUFFER, cameraUbo, 0, 64)
                });
        gpu.renderPassSetBindGroup(pass, 0, cameraBg);

        // Draw each entity
        for (var entity : entities) {
            // Upload object UBO: world matrix
            Mat4 world = entity.transform().toMatrix();
            uploadMatrix(objectUbo, world);

            // Upload material UBO: color vec3 (padded to 16 bytes)
            Vec3 color = entity.color();
            uploadVec3Padded(materialUbo, color);

            // Create per-draw bind groups
            long objectBg = gpu.deviceCreateBindGroup(deviceId, bindGroupLayout1,
                    new WgpuBindings.BindGroupEntry[]{
                            new WgpuBindings.BindGroupEntry(0,
                                    WgpuBindings.BindingResourceType.BUFFER, objectUbo, 0, 64)
                    });
            long materialBg = gpu.deviceCreateBindGroup(deviceId, bindGroupLayout2,
                    new WgpuBindings.BindGroupEntry[]{
                            new WgpuBindings.BindGroupEntry(0,
                                    WgpuBindings.BindingResourceType.BUFFER, materialUbo, 0, 16)
                    });

            gpu.renderPassSetBindGroup(pass, 1, objectBg);
            gpu.renderPassSetBindGroup(pass, 2, materialBg);

            gpu.renderPassDrawIndexed(pass, indexCount, 1, 0, 0, 0);

            // Release per-draw bind groups
            gpu.bindGroupRelease(objectBg);
            gpu.bindGroupRelease(materialBg);
        }

        gpu.renderPassEnd(pass);

        // Submit
        long cmdBuf = gpu.commandEncoderFinish(encoder);
        gpu.queueSubmit(queueId, cmdBuf);

        // Release per-frame resources
        gpu.bindGroupRelease(cameraBg);
    }

    // ── Shader compilation helper ────────────────────────────────────────

    private static String[] tryCompileSlang(String source, String variant) {
        try {
            consoleLog("[WebRenderer] Trying Slang " + variant + " variant...");
            String[] wgsl = TeaVmSlangCompiler.compile(source, "vertexMain", "fragmentMain");
            consoleLog("[WebRenderer] Slang " + variant + " compilation succeeded");
            consoleLog("[WebRenderer] Vertex WGSL:\n" + wgsl[0]);
            consoleLog("[WebRenderer] Fragment WGSL:\n" + wgsl[1]);
            return wgsl;
        } catch (Exception e) {
            consoleLog("[WebRenderer] Slang " + variant + " failed: " + e.getMessage());
            return null;
        }
    }

    // ── Cube mesh creation ───────────────────────────────────────────────
    // Mirrors PrimitiveMeshes.cube(): 24 vertices (4 per face), 36 indices

    private void createCubeMesh() {
        float s = 0.5f;
        float[] vertices = new float[24 * VERTEX_COMPONENTS];
        int vi = 0;

        // +Z face
        vi = face(vertices, vi, -s,-s,s, s,-s,s, s,s,s, -s,s,s, 0,0,1);
        // -Z face
        vi = face(vertices, vi, s,-s,-s, -s,-s,-s, -s,s,-s, s,s,-s, 0,0,-1);
        // +Y face
        vi = face(vertices, vi, -s,s,s, s,s,s, s,s,-s, -s,s,-s, 0,1,0);
        // -Y face
        vi = face(vertices, vi, -s,-s,-s, s,-s,-s, s,-s,s, -s,-s,s, 0,-1,0);
        // +X face
        vi = face(vertices, vi, s,-s,s, s,-s,-s, s,s,-s, s,s,s, 1,0,0);
        // -X face
        vi = face(vertices, vi, -s,-s,-s, -s,-s,s, -s,s,s, -s,s,-s, -1,0,0);

        int[] indices = new int[36];
        for (int f = 0; f < 6; f++) {
            int b = f * 4;
            indices[f * 6]     = b;
            indices[f * 6 + 1] = b + 1;
            indices[f * 6 + 2] = b + 2;
            indices[f * 6 + 3] = b;
            indices[f * 6 + 4] = b + 2;
            indices[f * 6 + 5] = b + 3;
        }

        indexCount = indices.length;

        // Upload vertex buffer
        vertexBufferSize = vertices.length * 4;
        vertexBuffer = gpu.deviceCreateBuffer(deviceId, vertexBufferSize,
                WgpuBindings.BUFFER_USAGE_VERTEX | WgpuBindings.BUFFER_USAGE_COPY_DST);
        writeFloats(queueId, (int) vertexBuffer, vertices);

        // Upload index buffer
        indexBufferSize = indices.length * 4;
        indexBuffer = gpu.deviceCreateBuffer(deviceId, indexBufferSize,
                WgpuBindings.BUFFER_USAGE_INDEX | WgpuBindings.BUFFER_USAGE_COPY_DST);
        writeInts(queueId, (int) indexBuffer, indices);
    }

    private static int face(float[] v, int vi,
                            float x0, float y0, float z0, float x1, float y1, float z1,
                            float x2, float y2, float z2, float x3, float y3, float z3,
                            float nx, float ny, float nz) {
        float[][] pos = {{x0,y0,z0},{x1,y1,z1},{x2,y2,z2},{x3,y3,z3}};
        float[][] uv = {{0,0},{1,0},{1,1},{0,1}};
        for (int i = 0; i < 4; i++) {
            v[vi++] = pos[i][0]; v[vi++] = pos[i][1]; v[vi++] = pos[i][2]; // position
            v[vi++] = nx; v[vi++] = ny; v[vi++] = nz;                       // normal
            v[vi++] = uv[i][0]; v[vi++] = uv[i][1];                         // uv
        }
        return vi;
    }

    // ── Depth texture ────────────────────────────────────────────────────

    private void createDepthResources(int width, int height) {
        depthWidth = width;
        depthHeight = height;
        depthTexture = gpu.deviceCreateTexture(deviceId, width, height, 1,
                WgpuBindings.TEXTURE_FORMAT_DEPTH24_PLUS,
                WgpuBindings.TEXTURE_DIMENSION_2D,
                WgpuBindings.TEXTURE_USAGE_RENDER_ATTACHMENT);
        depthTextureView = gpu.textureCreateView(depthTexture,
                WgpuBindings.TEXTURE_FORMAT_DEPTH24_PLUS,
                WgpuBindings.TEXTURE_VIEW_DIMENSION_2D, 1);
    }

    private void destroyDepthResources() {
        if (depthTextureView > 0) gpu.textureViewRelease(depthTextureView);
        if (depthTexture > 0) gpu.textureRelease(depthTexture);
        depthTexture = 0;
        depthTextureView = 0;
    }

    // ── GPU data upload via JS interop ───────────────────────────────────

    private void uploadMatrix(long buffer, Mat4 m) {
        // Upload as column-major for WGSL mat4x4f (which expects column-major storage)
        // Our Mat4 is row-major (m00,m01,m02,m03 is row 0).
        // WGSL mat4x4f stores column-by-column, so we transpose on upload.
        float[] data = {
                m.m00(), m.m10(), m.m20(), m.m30(), // column 0
                m.m01(), m.m11(), m.m21(), m.m31(), // column 1
                m.m02(), m.m12(), m.m22(), m.m32(), // column 2
                m.m03(), m.m13(), m.m23(), m.m33(), // column 3
        };
        writeFloats(queueId, (int) buffer, data);
    }

    private void uploadVec3Padded(long buffer, Vec3 v) {
        float[] data = {v.x(), v.y(), v.z(), 0f}; // padded to 16 bytes
        writeFloats(queueId, (int) buffer, data);
    }

    @JSBody(params = {"queueId", "bufferId", "data"}, script = """
        var queue = window._wgpu[queueId];
        var buf = window._wgpu[bufferId];
        queue.writeBuffer(buf, 0, new Float32Array(data));
    """)
    private static native void writeFloats(int queueId, int bufferId, float[] data);

    @JSBody(params = {"queueId", "bufferId", "data"}, script = """
        var queue = window._wgpu[queueId];
        var buf = window._wgpu[bufferId];
        queue.writeBuffer(buf, 0, new Uint32Array(data));
    """)
    private static native void writeInts(int queueId, int bufferId, int[] data);

    @JSBody(params = "msg", script = "console.log(msg);")
    private static native void consoleLog(String msg);
}
