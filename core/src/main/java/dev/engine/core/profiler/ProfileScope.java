package dev.engine.core.profiler;

public class ProfileScope implements AutoCloseable {

    private final Runnable onClose;

    ProfileScope(Runnable onClose) {
        this.onClose = onClose;
    }

    @Override
    public void close() {
        onClose.run();
    }
}
