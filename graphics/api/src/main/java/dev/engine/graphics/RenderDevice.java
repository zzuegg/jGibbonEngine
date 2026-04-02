package dev.engine.graphics;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferWriter;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.vertex.VertexFormat;

import java.nio.ByteBuffer;

public interface RenderDevice extends AutoCloseable {

    Handle createBuffer(BufferDescriptor descriptor);
    void destroyBuffer(Handle buffer);
    boolean isValidBuffer(Handle buffer);
    BufferWriter writeBuffer(Handle buffer);
    BufferWriter writeBuffer(Handle buffer, long offset, long length);

    Handle createTexture(TextureDescriptor descriptor);
    void uploadTexture(Handle texture, ByteBuffer pixels);
    void destroyTexture(Handle texture);
    boolean isValidTexture(Handle texture);

    Handle createVertexInput(VertexFormat format);
    void destroyVertexInput(Handle vertexInput);

    Handle createPipeline(PipelineDescriptor descriptor);
    void destroyPipeline(Handle pipeline);
    boolean isValidPipeline(Handle pipeline);

    RenderContext beginFrame();
    void endFrame(RenderContext context);

    <T> T queryCapability(RenderCapability<T> capability);

    @Override
    void close();
}
