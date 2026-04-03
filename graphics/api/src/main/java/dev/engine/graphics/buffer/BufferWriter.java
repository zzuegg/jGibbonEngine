package dev.engine.graphics.buffer;

import dev.engine.core.memory.NativeMemory;

public interface BufferWriter extends AutoCloseable {

    NativeMemory memory();

    @Override
    void close();
}
