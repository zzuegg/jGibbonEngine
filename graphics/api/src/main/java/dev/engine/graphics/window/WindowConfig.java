package dev.engine.graphics.window;

/**
 * Window configuration that is applied at startup.
 *
 * <p>Contains all window properties that should be set when the window is created.
 * Properties are applied via {@link WindowHandle#set} after window creation.
 *
 * <pre>{@code
 * var config = EngineConfig.builder()
 *     .windowTitle("My Game")
 *     .windowSize(1280, 720)
 *     .windowResizable(true)
 *     .windowDecorated(true)
 *     .windowVsync(true)
 *     .build();
 * }</pre>
 */
public record WindowConfig(
        String title,
        int width,
        int height,
        boolean resizable,
        boolean decorated,
        boolean vsync,
        boolean fullscreen,
        boolean alwaysOnTop
) {
    public WindowConfig {
        if (title == null) title = "Engine";
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
    }

    /** Creates a WindowDescriptor for the backend window creation call. */
    public WindowDescriptor toDescriptor() {
        return new WindowDescriptor(title, width, height);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String title = "Engine";
        private int width = 1280;
        private int height = 720;
        private boolean resizable = true;
        private boolean decorated = true;
        private boolean vsync = false;
        private boolean fullscreen = false;
        private boolean alwaysOnTop = false;

        public Builder title(String title)           { this.title = title; return this; }
        public Builder width(int width)              { this.width = width; return this; }
        public Builder height(int height)            { this.height = height; return this; }
        public Builder size(int width, int height)   { this.width = width; this.height = height; return this; }
        public Builder resizable(boolean v)          { this.resizable = v; return this; }
        public Builder decorated(boolean v)          { this.decorated = v; return this; }
        public Builder vsync(boolean v)              { this.vsync = v; return this; }
        public Builder fullscreen(boolean v)         { this.fullscreen = v; return this; }
        public Builder alwaysOnTop(boolean v)        { this.alwaysOnTop = v; return this; }

        public WindowConfig build() {
            return new WindowConfig(title, width, height, resizable, decorated, vsync, fullscreen, alwaysOnTop);
        }
    }
}
