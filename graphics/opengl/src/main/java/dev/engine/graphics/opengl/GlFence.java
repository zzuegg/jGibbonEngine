package dev.engine.graphics.opengl;

import dev.engine.graphics.sync.GpuFence;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryStack;

/**
 * OpenGL implementation of {@link GpuFence} using GL sync objects.
 */
public class GlFence implements GpuFence {

    private long sync;

    GlFence() {
        this.sync = GL45.glFenceSync(GL45.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
    }

    @Override
    public boolean isSignaled() {
        try (var stack = MemoryStack.stackPush()) {
            var buf = stack.mallocInt(1);
            GL45.glGetSynci(sync, GL45.GL_SYNC_STATUS, buf);
            return buf.get(0) == GL45.GL_SIGNALED;
        }
    }

    @Override
    public void waitFor() {
        GL45.glClientWaitSync(sync, GL45.GL_SYNC_FLUSH_COMMANDS_BIT, Long.MAX_VALUE);
    }

    @Override
    public boolean waitFor(long timeoutNanos) {
        int result = GL45.glClientWaitSync(sync, GL45.GL_SYNC_FLUSH_COMMANDS_BIT, timeoutNanos);
        return result == GL45.GL_ALREADY_SIGNALED || result == GL45.GL_CONDITION_SATISFIED;
    }

    @Override
    public void close() {
        if (sync != 0) {
            GL45.glDeleteSync(sync);
            sync = 0;
        }
    }
}
