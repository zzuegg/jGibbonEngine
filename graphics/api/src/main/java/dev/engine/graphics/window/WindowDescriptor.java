package dev.engine.graphics.window;

/**
 * Describes window creation parameters.
 *
 * <pre>{@code
 * var window = WindowDescriptor.builder("My Game")
 *     .size(1280, 720)
 *     .resizable(true)
 *     .fullscreen(false)
 *     .build();
 * }</pre>
 */
public record WindowDescriptor(
        String title,
        int width,
        int height,
        boolean resizable,
        boolean decorated,
        boolean fullscreen,
        boolean highDpi
) {
    /** Simple constructor for backward compatibility. */
    public WindowDescriptor(String title, int width, int height) {
        this(title, width, height, true, true, false, true);
    }

    public static Builder builder(String title) { return new Builder(title); }

    public static class Builder {
        private final String title;
        private int width = 1280;
        private int height = 720;
        private boolean resizable = true;
        private boolean decorated = true;
        private boolean fullscreen = false;
        private boolean highDpi = true;

        private Builder(String title) { this.title = title; }

        public Builder size(int width, int height) { this.width = width; this.height = height; return this; }
        public Builder resizable(boolean resizable) { this.resizable = resizable; return this; }
        public Builder decorated(boolean decorated) { this.decorated = decorated; return this; }
        public Builder fullscreen(boolean fullscreen) { this.fullscreen = fullscreen; return this; }
        public Builder highDpi(boolean highDpi) { this.highDpi = highDpi; return this; }

        public WindowDescriptor build() {
            return new WindowDescriptor(title, width, height, resizable, decorated, fullscreen, highDpi);
        }
    }
}
