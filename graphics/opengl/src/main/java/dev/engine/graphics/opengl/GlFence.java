package dev.engine.graphics.opengl;

import dev.engine.graphics.sync.GpuFence;

import java.nio.IntBuffer;

/**
 * OpenGL implementation of {@link GpuFence} using GL sync objects.
 */
public class GlFence implements GpuFence {

    private final GlBindings gl;
    private long sync;

    GlFence(GlBindings gl) {
        this.gl = gl;
        this.sync = gl.glFenceSync(GlBindings.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    @Override
    public boolean isSignaled() {
        var buf = IntBuffer.allocate(1);
        gl.glGetSynci(sync, GlBindings.GL_SYNC_STATUS, buf);
        return buf.get(0) == GlBindings.GL_SIGNALED;
    }

    @Override
    public void waitFor() {
        gl.glClientWaitSync(sync, GlBindings.GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE);
    }

    @Override
    public boolean waitFor(long timeoutNanos) {
        int result = gl.glClientWaitSync(sync, GlBindings.GL_SYNC_FLUSH_COMMANDS_BIT, timeoutNanos);
        return result == GlBindings.GL_ALREADY_SIGNALED || result == GlBindings.GL_CONDITION_SATISFIED;
    }

    @Override
    public void close() {
        if (sync != 0) {
            gl.glDeleteSync(sync);
            sync = 0;
        }
    }
}
