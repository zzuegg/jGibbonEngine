package dev.engine.web;

import dev.engine.graphics.webgpu.WgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuInit;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * Entry point for the TeaVM-compiled web application.
 *
 * <p>Renders a colored triangle using the browser's native WebGPU API.
 * Shaders are hard-coded WGSL since the Slang compiler is not available
 * in the browser environment.
 */
public class WebMain {

    // Hard-coded WGSL shaders for the triangle
    private static final String WGSL_SHADER = """
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

        @fragment
        fn fs_main(in: VertexOutput) -> @location(0) vec4f {
            return vec4f(in.color, 1.0);
        }
        """;

    // Interleaved vertex data: position (x,y,z) + color (r,g,b) per vertex
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

        // Create shader module
        long shaderModule = bindings.deviceCreateShaderModule(deviceId, WGSL_SHADER);

        // Create render pipeline (auto layout, no bind groups needed)
        var pipelineDesc = new WgpuBindings.RenderPipelineDescriptor(
                0,  // auto layout
                shaderModule, "vs_main",
                shaderModule, "fs_main",
                new WgpuBindings.VertexBufferLayoutDesc(
                        VERTEX_STRIDE,
                        WgpuBindings.VERTEX_STEP_MODE_VERTEX,
                        new WgpuBindings.VertexAttributeDesc[] {
                                new WgpuBindings.VertexAttributeDesc(
                                        WgpuBindings.VERTEX_FORMAT_FLOAT32X3, 0, 0),  // position
                                new WgpuBindings.VertexAttributeDesc(
                                        WgpuBindings.VERTEX_FORMAT_FLOAT32X3, 12, 1), // color
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

        setStatus("Rendering...");

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
