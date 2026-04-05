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
import dev.engine.graphics.pipeline.ShaderBinary;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.renderstate.BlendMode;
import dev.engine.graphics.renderstate.CullMode;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.shader.ShaderCompiler;
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
import java.util.List;

/**
 * Renders the debug UI overlay using the engine's graphics API.
 *
 * <p>Compiles the UI shader via Slang (cross-platform: GLSL, SPIRV, WGSL),
 * manages the font atlas texture, and converts {@link NkDrawList} output
 * into GPU draw calls each frame.
 */
public class DebugUiOverlay implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DebugUiOverlay.class);

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
     *
     * @param font the font to use for text rendering
     * @param compiler the shader compiler (Slang → GLSL/SPIRV/WGSL)
     */
    public void init(NkFont font, ShaderCompiler compiler) {
        // Detect target format from backend
        var backend = device.queryCapability(DeviceCapability.BACKEND_NAME);
        int slangTarget = switch (backend) {
            case "Vulkan" -> ShaderCompiler.TARGET_SPIRV;
            case "WebGPU" -> ShaderCompiler.TARGET_WGSL;
            default -> ShaderCompiler.TARGET_GLSL;
        };

        // Load and compile the Slang shader
        String slangSource = loadShaderResource("shaders/debug_ui.slang");
        pipeline = compileUiShader(slangSource, compiler, slangTarget);
        log.info("Debug UI shader compiled for target: {}", backend);

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

    private Handle<PipelineResource> compileUiShader(String source, ShaderCompiler compiler, int target) {
        var entryPoints = List.of(
                new ShaderCompiler.EntryPointDesc("vertexMain", ShaderCompiler.STAGE_VERTEX),
                new ShaderCompiler.EntryPointDesc("fragmentMain", ShaderCompiler.STAGE_FRAGMENT));

        try (var result = compiler.compile(source, entryPoints, target)) {
            if (target == ShaderCompiler.TARGET_SPIRV) {
                return device.createPipeline(PipelineDescriptor.ofSpirv(
                        new ShaderBinary(ShaderStage.VERTEX, result.codeBytes(0)),
                        new ShaderBinary(ShaderStage.FRAGMENT, result.codeBytes(1)))
                        .withVertexFormat(VERTEX_FORMAT));
            } else {
                // For WGSL, Slang preserves entry point names. For GLSL, Slang renames to "main".
                String vsEntry = (target == ShaderCompiler.TARGET_WGSL) ? "vertexMain" : "main";
                String fsEntry = (target == ShaderCompiler.TARGET_WGSL) ? "fragmentMain" : "main";
                return device.createPipeline(PipelineDescriptor.of(
                        new ShaderSource(ShaderStage.VERTEX, result.code(0), vsEntry),
                        new ShaderSource(ShaderStage.FRAGMENT, result.code(1), fsEntry))
                        .withVertexFormat(VERTEX_FORMAT));
            }
        }
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

        // Push constants for screen size
        ByteBuffer pushConstants = ByteBuffer.allocateDirect(8)
                .order(ByteOrder.nativeOrder())
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
