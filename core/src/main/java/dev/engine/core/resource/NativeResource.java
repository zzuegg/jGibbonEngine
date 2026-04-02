package dev.engine.core.resource;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class NativeResource implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Cleaner.Cleanable cleanable;

    protected NativeResource(Runnable releaseAction) {
        var action = new CleanupAction(releaseAction);
        this.cleanable = ResourceCleaner.register(this, action);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cleanable.clean();
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    private static class CleanupAction implements Runnable {
        private final Runnable release;
        private final AtomicBoolean executed = new AtomicBoolean(false);

        CleanupAction(Runnable release) { this.release = release; }

        @Override
        public void run() {
            if (executed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
