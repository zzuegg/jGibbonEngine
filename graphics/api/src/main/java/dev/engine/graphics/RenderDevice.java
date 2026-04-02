package dev.engine.graphics;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.buffer.BufferDescriptor;

public interface RenderDevice extends AutoCloseable {

    Handle createBuffer(BufferDescriptor descriptor);
    void destroyBuffer(Handle buffer);
    boolean isValidBuffer(Handle buffer);

    RenderContext beginFrame();
    void endFrame(RenderContext context);

    <T> T queryCapability(RenderCapability<T> capability);

    @Override
    void close();
}
