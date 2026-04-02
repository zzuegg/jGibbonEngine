package dev.engine.graphics.buffer;

import dev.engine.core.handle.Handle;

import java.lang.foreign.MemorySegment;

public interface BufferWriter extends AutoCloseable {

    MemorySegment segment();

    @Override
    void close();
}
