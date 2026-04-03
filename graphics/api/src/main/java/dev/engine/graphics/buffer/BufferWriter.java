package dev.engine.graphics.buffer;

import dev.engine.core.gpu.GpuMemory;

public interface BufferWriter extends AutoCloseable {

    GpuMemory memory();

    @Override
    void close();
}
