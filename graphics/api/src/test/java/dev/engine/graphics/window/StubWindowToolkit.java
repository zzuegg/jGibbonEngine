package dev.engine.graphics.window;

class StubWindowToolkit implements WindowToolkit {

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        return new StubWindowHandle(descriptor);
    }

    @Override
    public void pollEvents() {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    private static class StubWindowHandle implements WindowHandle {
        private final WindowDescriptor desc;
        private boolean open = true;

        StubWindowHandle(WindowDescriptor desc) { this.desc = desc; }

        @Override public boolean isOpen() { return open; }
        @Override public int width() { return desc.width(); }
        @Override public int height() { return desc.height(); }
        @Override public String title() { return desc.title(); }
        @Override public void close() { open = false; }
    }
}
