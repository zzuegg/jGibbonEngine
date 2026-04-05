package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.core.property.PropertyMap;
import dev.engine.graphics.*;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.renderstate.BlendMode;
import dev.engine.graphics.renderstate.CullMode;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.ui.NkContext;
import dev.engine.ui.NkDrawList;
import dev.engine.ui.NkFont;

import java.nio.ByteBuffer;

/**
 * Renders the debug UI overlay using the engine's graphics API.
 *
 * <p>Creates a pipeline with a simple 2D shader, manages the font atlas texture,
 * and converts {@link NkDrawList} output into GPU draw calls each frame.
 */
public class DebugUiOverlay implements AutoCloseable {

    private static final String VERTEX_SHADER = """
            #version 450
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUV;
            layout(location = 2) in vec4 aColor;

            layout(location = 0) out vec2 vUV;
            layout(location = 1) out vec4 vColor;

            layout(push_constant) uniform PushConstants {
                vec2 screenSize;
            } pc;

            void main() {
                // Convert from screen coordinates to clip space
                vec2 pos = aPos / pc.screenSize * 2.0 - 1.0;
                pos.y = -pos.y; // Flip Y for screen-space origin at top-left
                gl_Position = vec4(pos, 0.0, 1.0);
                vUV = aUV;
                vColor = aColor;
            }
            """;

    private static final String FRAGMENT_SHADER = """
            #version 450
            layout(location = 0) in vec2 vUV;
            layout(location = 1) in vec4 vColor;

            layout(binding = 0) uniform sampler2D uFontAtlas;

            layout(location = 0) out vec4 fragColor;

            void main() {
                vec4 texColor = texture(uFontAtlas, vUV);
                fragColor = vColor * texColor;
            }
            """;

    // Vertex format: pos(2f) + uv(2f) + color(4 normalized bytes) = 20 bytes
    private static final VertexFormat VERTEX_FORMAT = VertexFormat.of(
            new VertexAttribute(0, 2, ComponentType.FLOAT, false, 0),   // position
            new VertexAttribute(1, 2, ComponentType.FLOAT, false, 8),   // texcoord
            new VertexAttribute(2, 4, ComponentType.BYTE, true, 16)     // color (normalized)
    );

    private final RenderDevice device;
    private Handle<PipelineResource> pipeline;
    private Handle<TextureResource> fontTexture;
    private Handle<SamplerResource> fontSampler;
    private Handle<VertexInputResource> vertexInput;
    private Handle<BufferResource> vertexBuffer;
    private Handle<BufferResource> indexBuffer;

    private final NkDrawList drawList = new NkDrawList();

    private int currentVbSize;
    private int currentIbSize;

    private boolean initialized;

    public DebugUiOverlay(RenderDevice device) {
        this.device = device;
    }

    /**
     * Initializes GPU resources. Must be called after the device is ready.
     */
    public void init(NkFont font) {
        // Create pipeline
        var pipelineDesc = PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VERTEX_SHADER),
                new ShaderSource(ShaderStage.FRAGMENT, FRAGMENT_SHADER)
        ).withVertexFormat(VERTEX_FORMAT);
        pipeline = device.createPipeline(pipelineDesc);

        // Create font atlas texture
        var texDesc = TextureDescriptor.rgba(font.atlasWidth(), font.atlasHeight());
        fontTexture = device.createTexture(texDesc);
        device.uploadTexture(fontTexture, ByteBuffer.wrap(font.atlasData()));

        // Create sampler (nearest for pixel-perfect font rendering)
        fontSampler = device.createSampler(SamplerDescriptor.nearest());

        // Create vertex input
        vertexInput = device.createVertexInput(VERTEX_FORMAT);

        // Initial buffers (will be resized as needed)
        currentVbSize = 64 * 1024;
        currentIbSize = 32 * 1024;
        vertexBuffer = device.createBuffer(new BufferDescriptor(currentVbSize, BufferUsage.VERTEX, AccessPattern.STREAM));
        indexBuffer = device.createBuffer(new BufferDescriptor(currentIbSize, BufferUsage.INDEX, AccessPattern.STREAM));

        initialized = true;
    }

    /**
     * Renders the UI overlay for the current frame.
     *
     * @param ctx the UI context to render
     * @param viewportWidth the current viewport width
     * @param viewportHeight the current viewport height
     */
    public void render(NkContext ctx, int viewportWidth, int viewportHeight) {
        if (!initialized || ctx.drawCommands().isEmpty()) return;

        // Convert draw commands to vertex/index buffers
        drawList.convert(ctx.drawCommands(), ctx.font());

        if (drawList.vertexCount() == 0 || drawList.indexCount() == 0) return;

        ByteBuffer vData = drawList.vertexData();
        ByteBuffer iData = drawList.indexData();

        // Resize buffers if needed
        if (vData.remaining() > currentVbSize) {
            device.destroyBuffer(vertexBuffer);
            currentVbSize = vData.remaining() * 2;
            vertexBuffer = device.createBuffer(new BufferDescriptor(currentVbSize, BufferUsage.VERTEX, AccessPattern.STREAM));
        }
        if (iData.remaining() > currentIbSize) {
            device.destroyBuffer(indexBuffer);
            currentIbSize = iData.remaining() * 2;
            indexBuffer = device.createBuffer(new BufferDescriptor(currentIbSize, BufferUsage.INDEX, AccessPattern.STREAM));
        }

        // Upload vertex data
        try (var writer = device.writeBuffer(vertexBuffer, 0, vData.remaining())) {
            var mem = writer.memory();
            for (long i = 0; i < vData.remaining(); i++) {
                mem.putByte(i, vData.get(vData.position() + (int) i));
            }
        }

        // Upload index data
        try (var writer = device.writeBuffer(indexBuffer, 0, iData.remaining())) {
            var mem = writer.memory();
            for (long i = 0; i < iData.remaining(); i++) {
                mem.putByte(i, iData.get(iData.position() + (int) i));
            }
        }

        // Push constants for screen size
        ByteBuffer pushConstants = ByteBuffer.allocateDirect(8)
                .order(java.nio.ByteOrder.nativeOrder())
                .putFloat(viewportWidth)
                .putFloat(viewportHeight)
                .flip();

        // Render batches
        for (var batch : drawList.batches()) {
            var rec = new CommandRecorder();

            // Set UI render state: alpha blending, no depth, no cull
            rec.setRenderState(PropertyMap.<RenderState>builder()
                    .set(RenderState.DEPTH_TEST, false)
                    .set(RenderState.DEPTH_WRITE, false)
                    .set(RenderState.BLEND_MODE, BlendMode.ALPHA)
                    .set(RenderState.CULL_MODE, CullMode.NONE)
                    .set(RenderState.SCISSOR_TEST, true)
                    .build());

            rec.scissor(batch.scissorX(), batch.scissorY(),
                    batch.scissorW(), batch.scissorH());

            rec.bindPipeline(pipeline);
            rec.pushConstants(pushConstants.duplicate());

            rec.bindTexture(0, fontTexture);
            rec.bindSampler(0, fontSampler);

            rec.bindVertexBuffer(vertexBuffer, vertexInput);
            rec.bindIndexBuffer(indexBuffer);

            rec.drawIndexed(batch.indexCount(), batch.indexOffset());

            device.submit(rec.finish());
        }

        // Restore scissor
        var restore = new CommandRecorder();
        restore.setRenderState(PropertyMap.<RenderState>builder()
                .set(RenderState.SCISSOR_TEST, false)
                .build());
        device.submit(restore.finish());
    }

    @Override
    public void close() {
        if (!initialized) return;
        if (vertexBuffer != null) device.destroyBuffer(vertexBuffer);
        if (indexBuffer != null) device.destroyBuffer(indexBuffer);
        if (fontTexture != null) device.destroyTexture(fontTexture);
        if (fontSampler != null) device.destroySampler(fontSampler);
        if (vertexInput != null) device.destroyVertexInput(vertexInput);
        if (pipeline != null) device.destroyPipeline(pipeline);
        initialized = false;
    }
}
