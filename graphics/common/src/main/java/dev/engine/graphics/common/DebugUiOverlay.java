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
import dev.engine.graphics.renderstate.BlendMode;
import dev.engine.graphics.renderstate.CullMode;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.ui.NkContext;
import dev.engine.ui.NkDrawList;
import dev.engine.ui.NkFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Renders the debug UI overlay using the engine's graphics API.
 *
 * <p>Compiles the UI shader via {@link ShaderManager} (Slang → GLSL/SPIRV/WGSL),
 * manages the font atlas texture, and converts {@link NkDrawList} output
 * into GPU draw calls each frame.
 */
public class DebugUiOverlay implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DebugUiOverlay.class);

    // Vertex format: pos(2f) + uv(2f) + color(4 normalized bytes) = 20 bytes
    private static final VertexFormat VERTEX_FORMAT = VertexFormat.of(
            new VertexAttribute(0, 2, ComponentType.FLOAT, false, 0),   // position
            new VertexAttribute(1, 2, ComponentType.FLOAT, false, 8),   // texcoord
            new VertexAttribute(2, 4, ComponentType.UNSIGNED_BYTE, true, 16)  // color (normalized)
    );

    private final RenderDevice device;
    private boolean flipScissorY; // OpenGL uses bottom-left scissor origin
    private Handle<PipelineResource> pipeline;
    private Handle<TextureResource> fontTexture;
    private Handle<SamplerResource> fontSampler;
    private Handle<VertexInputResource> vertexInput;
    private Handle<BufferResource> vertexBuffer;
    private Handle<BufferResource> indexBuffer;
    private Handle<BufferResource> uniformBuffer;
    private int uboBinding;
    private int textureBinding;

    private final NkDrawList drawList = new NkDrawList();

    private int currentVbSize;
    private int currentIbSize;

    private boolean initialized;

    public DebugUiOverlay(RenderDevice device) {
        this.device = device;
    }

    /**
     * Initializes GPU resources. Must be called after the device is ready.
     *
     * @param font the font to use for text rendering
     * @param shaderManager compiles the Slang shader to the correct backend target
     */
    public void init(NkFont font, ShaderManager shaderManager) {
        // Load and compile the Slang shader via ShaderManager.
        // Inject the correct texture binding offset so SPIRV bindings match the descriptor layout.
        int texOffset = shaderManager.textureBindingOffset();
        String slangSource = loadShaderResource("shaders/debug_ui.slang")
                .replace("TEXTURE_BINDING", String.valueOf(texOffset));
        CompiledShader compiled;
        try {
            compiled = shaderManager.compileSlangSource(slangSource, "debug_ui", VERTEX_FORMAT);
        } catch (Exception e) {
            log.warn("Debug UI shader compilation failed — overlay disabled: {}", e.getMessage());
            return;
        }
        pipeline = compiled.pipeline();

        // Get binding indices from shader reflection
        uboBinding = compiled.bindingIndex("ScreenData");
        textureBinding = compiled.bindingIndex("fontAtlas");
        flipScissorY = "OpenGL".equals(device.queryCapability(dev.engine.graphics.DeviceCapability.BACKEND_NAME));
        log.debug("DebugUI shader bindings: UBO={}, texture={}, texOffset={}, flipScissorY={}", uboBinding, textureBinding, texOffset, flipScissorY);

        // Create font atlas texture (no mipmaps — sampled with nearest filtering)
        var texDesc = new TextureDescriptor(
                dev.engine.graphics.texture.TextureType.TEXTURE_2D,
                font.atlasWidth(), font.atlasHeight(), 1, 1,
                dev.engine.graphics.texture.TextureFormat.RGBA8,
                dev.engine.graphics.texture.MipMode.NONE);
        fontTexture = device.createTexture(texDesc);
        // Use a direct buffer for the upload — some GL drivers require it for DSA functions
        byte[] atlasBytes = font.atlasData();
        ByteBuffer directAtlas = ByteBuffer.allocateDirect(atlasBytes.length).order(java.nio.ByteOrder.nativeOrder());
        directAtlas.put(atlasBytes).flip();
        device.uploadTexture(fontTexture, directAtlas);

        // Create sampler (nearest for pixel-perfect font rendering)
        fontSampler = device.createSampler(SamplerDescriptor.nearest());

        // Create vertex input
        vertexInput = device.createVertexInput(VERTEX_FORMAT);

        // Initial buffers (will be resized as needed)
        currentVbSize = 64 * 1024;
        currentIbSize = 32 * 1024;
        vertexBuffer = device.createBuffer(new BufferDescriptor(currentVbSize, BufferUsage.VERTEX, AccessPattern.STREAM));
        indexBuffer = device.createBuffer(new BufferDescriptor(currentIbSize, BufferUsage.INDEX, AccessPattern.STREAM));
        // WebGPU requires 16-byte minimum uniform buffer binding size
        uniformBuffer = device.createBuffer(new BufferDescriptor(16, BufferUsage.UNIFORM, AccessPattern.STREAM));

        initialized = true;
    }

    private static String loadShaderResource(String path) {
        try (InputStream is = DebugUiOverlay.class.getClassLoader().getResourceAsStream(path)) {
            if (is != null) return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load UI shader: {}", path, e);
        }
        throw new RuntimeException("Debug UI shader not found on classpath: " + path);
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

        // Upload screen size to uniform buffer (16 bytes for WebGPU alignment)
        try (var writer = device.writeBuffer(uniformBuffer, 0, 16)) {
            var mem = writer.memory();
            mem.putFloat(0, viewportWidth);
            mem.putFloat(4, viewportHeight);
        }

        // Render batches
        for (var batch : drawList.batches()) {
            var rec = new CommandRecorder();

            // Bind pipeline first — Vulkan needs it before setRenderState for blend mode
            rec.bindPipeline(pipeline);

            rec.setRenderState(PropertyMap.<RenderState>builder()
                    .set(RenderState.DEPTH_TEST, false)
                    .set(RenderState.DEPTH_WRITE, false)
                    .set(RenderState.BLEND_MODE, BlendMode.ALPHA)
                    .set(RenderState.CULL_MODE, CullMode.NONE)
                    .set(RenderState.SCISSOR_TEST, true)
                    .build());

            // Scissor clamping is handled by the backend (WgpuRenderDevice clamps to RT dimensions)
            int sy = flipScissorY
                    ? viewportHeight - batch.scissorY() - batch.scissorH()
                    : batch.scissorY();
            rec.scissor(batch.scissorX(), sy, batch.scissorW(), batch.scissorH());

            rec.bindUniformBuffer(uboBinding, uniformBuffer);

            rec.bindTexture(textureBinding, fontTexture);
            rec.bindSampler(textureBinding, fontSampler);

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
        if (uniformBuffer != null) device.destroyBuffer(uniformBuffer);
        if (fontTexture != null) device.destroyTexture(fontTexture);
        if (fontSampler != null) device.destroySampler(fontSampler);
        if (vertexInput != null) device.destroyVertexInput(vertexInput);
        if (pipeline != null) device.destroyPipeline(pipeline);
        initialized = false;
    }
}
