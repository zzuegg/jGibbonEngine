package dev.engine.web;

import dev.engine.graphics.webgpu.WgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmSlangCompiler;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuInit;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Entry point for the TeaVM-compiled web application.
 *
 * <p>Renders a colored triangle using the browser's native WebGPU API.
 * Shaders are compiled from Slang source via the Slang WASM compiler when
 * available, falling back to hardcoded WGSL otherwise.
 */
public class WebMain {

    // Slang shader source — same as graphics/common/src/main/resources/shaders/unlit.slang
    // but self-contained (interfaces + param blocks inlined) so it compiles standalone.
    private static final String SLANG_SHADER = """
        // Inline interfaces and param blocks for standalone compilation

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

    // Fallback hard-coded WGSL shaders (simple passthrough triangle)
    private static final String FALLBACK_VERTEX_WGSL = """
        struct VertexOutput {
            @builtin(position) position: vec4f,
            @location(0) color: vec3f,
        }

        @vertex
        fn vs_main(@location(0) position: vec3f, @location(1) color: vec3f) -> VertexOutput {
            var out: VertexOutput;
            out.position = vec4f(position, 1.0);
            out.color = color;
            return out;
        }
        """;

    private static final String FALLBACK_FRAGMENT_WGSL = """
        struct VertexOutput {
            @builtin(position) position: vec4f,
            @location(0) color: vec3f,
        }

        @fragment
        fn fs_main(in: VertexOutput) -> @location(0) vec4f {
            return vec4f(in.color, 1.0);
        }
        """;

    // Interleaved vertex data: position (x,y,z) + color/normal (r,g,b) per vertex
    private static final float[] TRIANGLE_VERTICES = {
        //  x      y     z      r    g    b
         0.0f,  0.5f, 0.0f,  1.0f, 0.0f, 0.0f,  // top - red
        -0.5f, -0.5f, 0.0f,  0.0f, 1.0f, 0.0f,  // bottom-left - green
         0.5f, -0.5f, 0.0f,  0.0f, 0.0f, 1.0f,  // bottom-right - blue
    };

    private static final int VERTEX_STRIDE = 6 * 4; // 6 floats * 4 bytes
    private static final int VERTEX_COUNT = 3;

    // Rendering state
    private static TeaVmWgpuBindings bindings;
    private static int deviceId;
    private static int queueId;
    private static int contextId;
    private static long pipeline;
    private static long vertexBuffer;
    private static int vertexBufferSize;
    private static boolean slangCompiled;

    // Entry point names from Slang compilation
    private static String vertexEntryName = "vs_main";
    private static String fragmentEntryName = "fs_main";

    @JSFunctor
    public interface FrameCallback extends JSObject {
        void onFrame();
    }

    @JSBody(params = "callback", script = """
        function loop() {
            callback();
            requestAnimationFrame(loop);
        }
        requestAnimationFrame(loop);
    """)
    private static native void requestAnimationFrame(FrameCallback callback);

    @JSBody(params = "msg", script = """
        var el = document.getElementById('status');
        if (el) el.textContent = msg;
    """)
    private static native void setStatus(String msg);

    @JSBody(params = "msg", script = "console.log(msg);")
    private static native void consoleLog(String msg);

    public static void main(String[] args) {
        setStatus("Initializing WebGPU...");

        bindings = new TeaVmWgpuBindings();
        if (!bindings.isAvailable()) {
            setStatus("WebGPU is not available in this browser.");
            return;
        }
        bindings.initialize();

        // Async init: request adapter + device
        deviceId = TeaVmWgpuInit.initAsync();
        if (deviceId <= 0) {
            setStatus("Failed to initialize WebGPU adapter/device.");
            return;
        }

        queueId = (int) bindings.deviceGetQueue(deviceId);

        // Configure the canvas context
        contextId = TeaVmWgpuBindings.configureCanvasContext("canvas", deviceId);
        String canvasFormat = TeaVmWgpuBindings.getPreferredCanvasFormat();

        // Compile shaders — try Slang WASM first, fall back to hardcoded WGSL
        String vertexWGSL;
        String fragmentWGSL;

        if (TeaVmSlangCompiler.isAvailable()) {
            String version = TeaVmSlangCompiler.getVersionString();
            consoleLog("[Slang WASM] Compiler available, version: " + version);
            setStatus("Compiling Slang shaders to WGSL...");

            try {
                String[] wgsl = TeaVmSlangCompiler.compile(SLANG_SHADER, "vertexMain", "fragmentMain");
                vertexWGSL = wgsl[0];
                fragmentWGSL = wgsl[1];
                vertexEntryName = "vertexMain";
                fragmentEntryName = "fragmentMain";
                slangCompiled = true;
                consoleLog("[Slang WASM] Vertex WGSL:\n" + vertexWGSL);
                consoleLog("[Slang WASM] Fragment WGSL:\n" + fragmentWGSL);
            } catch (Exception e) {
                consoleLog("[Slang WASM] Compilation failed: " + e.getMessage() + ", using fallback WGSL");
                vertexWGSL = FALLBACK_VERTEX_WGSL;
                fragmentWGSL = FALLBACK_FRAGMENT_WGSL;
            }
        } else {
            consoleLog("[Slang WASM] Not available, using fallback WGSL shaders");
            vertexWGSL = FALLBACK_VERTEX_WGSL;
            fragmentWGSL = FALLBACK_FRAGMENT_WGSL;
        }

        // Create shader modules — one combined WGSL or two separate modules
        // WebGPU allows vertex and fragment from different modules
        long vertexShaderModule = bindings.deviceCreateShaderModule(deviceId, vertexWGSL);
        long fragmentShaderModule = bindings.deviceCreateShaderModule(deviceId, fragmentWGSL);

        // Create render pipeline
        var pipelineDesc = new WgpuBindings.RenderPipelineDescriptor(
                0,  // auto layout
                vertexShaderModule, vertexEntryName,
                fragmentShaderModule, fragmentEntryName,
                new WgpuBindings.VertexBufferLayoutDesc(
                        VERTEX_STRIDE,
                        WgpuBindings.VERTEX_STEP_MODE_VERTEX,
                        new WgpuBindings.VertexAttributeDesc[] {
                                new WgpuBindings.VertexAttributeDesc(
                                        WgpuBindings.VERTEX_FORMAT_FLOAT32X3, 0, 0),  // position
                                new WgpuBindings.VertexAttributeDesc(
                                        WgpuBindings.VERTEX_FORMAT_FLOAT32X3, 12, 1), // color/normal
                        }
                ),
                WgpuBindings.PRIMITIVE_TOPOLOGY_TRIANGLE_LIST,
                WgpuBindings.FRONT_FACE_CCW,
                WgpuBindings.CULL_MODE_NONE,
                0, 0, 0, 0, 0,     // no depth/stencil
                null, null,         // no stencil face state
                mapCanvasFormat(canvasFormat),
                WgpuBindings.BLEND_FACTOR_ONE,
                WgpuBindings.BLEND_FACTOR_ZERO,
                WgpuBindings.BLEND_OP_ADD,
                WgpuBindings.BLEND_FACTOR_ONE,
                WgpuBindings.BLEND_FACTOR_ZERO,
                WgpuBindings.BLEND_OP_ADD
        );
        pipeline = bindings.deviceCreateRenderPipeline(deviceId, pipelineDesc);

        // Create and upload vertex buffer
        vertexBufferSize = TRIANGLE_VERTICES.length * 4;
        vertexBuffer = bindings.deviceCreateBuffer(deviceId, vertexBufferSize,
                WgpuBindings.BUFFER_USAGE_VERTEX | WgpuBindings.BUFFER_USAGE_COPY_DST);
        writeVertexData(queueId, (int) vertexBuffer, TRIANGLE_VERTICES);

        if (slangCompiled) {
            setStatus("Rendering (Slang -> WGSL)");
        } else {
            setStatus("Rendering (fallback WGSL)");
        }

        // Enter the render loop
        requestAnimationFrame(WebMain::renderFrame);
    }

    @JSBody(params = {"queueId", "bufferId", "data"}, script = """
        var queue = window._wgpu[queueId];
        var buf = window._wgpu[bufferId];
        var f32 = new Float32Array(data);
        queue.writeBuffer(buf, 0, f32);
    """)
    private static native void writeVertexData(int queueId, int bufferId, float[] data);

    private static void renderFrame() {
        // Get the current canvas texture view
        int textureViewId = TeaVmWgpuBindings.getCurrentTextureView(contextId);

        // Create command encoder
        long encoder = bindings.deviceCreateCommandEncoder(deviceId);

        // Begin render pass
        var colorAttachment = new WgpuBindings.ColorAttachment(
                textureViewId, 0.05f, 0.05f, 0.1f, 1.0f);
        var renderPassDesc = new WgpuBindings.RenderPassDescriptor(
                new WgpuBindings.ColorAttachment[]{ colorAttachment }, null);
        long renderPass = bindings.commandEncoderBeginRenderPass(encoder, renderPassDesc);

        // Draw the triangle
        bindings.renderPassSetPipeline(renderPass, pipeline);
        bindings.renderPassSetVertexBuffer(renderPass, 0, vertexBuffer, 0, vertexBufferSize);
        bindings.renderPassDraw(renderPass, VERTEX_COUNT, 1, 0, 0);
        bindings.renderPassEnd(renderPass);

        // Submit
        long cmdBuf = bindings.commandEncoderFinish(encoder);
        bindings.queueSubmit(queueId, cmdBuf);

        // Release per-frame texture view
        TeaVmWgpuBindings.wgpuRelease(textureViewId);
    }

    private static int mapCanvasFormat(String format) {
        return switch (format) {
            case "rgba8unorm" -> WgpuBindings.TEXTURE_FORMAT_RGBA8_UNORM;
            case "bgra8unorm" -> WgpuBindings.TEXTURE_FORMAT_BGRA8_UNORM;
            default -> WgpuBindings.TEXTURE_FORMAT_BGRA8_UNORM;
        };
    }
}
