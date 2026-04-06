package dev.engine.graphics.common.engine;

import dev.engine.core.math.Vec2i;
import dev.engine.core.scene.AbstractScene;
import dev.engine.graphics.GraphicsBackendFactory;
import dev.engine.graphics.GraphicsConfig;
import dev.engine.graphics.window.WindowDescriptor;

/**
 * Configuration for the Engine. Determines threading mode, backend, window, scene type.
 *
 * <pre>{@code
 * var config = EngineConfig.builder()
 *     .windowTitle("My Game")
 *     .windowSize(1280, 720)
 *     .debugOverlay(true)
 *     .platform(DesktopPlatform.builder().build())
 *     .graphics(new OpenGlConfig(toolkit, glBindings))
 *     .build();
 *
 * new MyGame().launch(config);
 * }</pre>
 */
public record EngineConfig(
        boolean headless,
        boolean threaded,
        WindowDescriptor window,
        AbstractScene scene,
        int maxFrames,
        boolean debugOverlay,
        Platform platform,
        GraphicsConfig graphics,
        GraphicsBackendFactory graphicsBackend
) {
    /** Convenience: window title from descriptor. */
    public String windowTitle() { return window.title(); }
    /** Convenience: window size from descriptor. */
    public Vec2i windowSize() { return new Vec2i(window.width(), window.height()); }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean headless = false;
        private boolean threaded = false;
        private WindowDescriptor window = null;
        private String windowTitle = "Engine";
        private int windowWidth = 1280;
        private int windowHeight = 720;
        private AbstractScene scene = null;
        private int maxFrames = 0; // 0 = unlimited
        private boolean debugOverlay = true;
        private Platform platform = null;
        private GraphicsConfig graphics = null;
        private GraphicsBackendFactory graphicsBackend = null;

        public Builder headless(boolean headless) { this.headless = headless; return this; }
        public Builder threaded(boolean threaded) { this.threaded = threaded; return this; }
        /** Sets the full window descriptor. Overrides windowTitle/windowSize. */
        public Builder window(WindowDescriptor window) { this.window = window; return this; }
        public Builder windowTitle(String title) { this.windowTitle = title; return this; }
        public Builder windowSize(Vec2i size) { this.windowWidth = size.x(); this.windowHeight = size.y(); return this; }
        public Builder windowSize(int w, int h) { this.windowWidth = w; this.windowHeight = h; return this; }
        public Builder scene(AbstractScene scene) { this.scene = scene; return this; }
        public Builder maxFrames(int maxFrames) { this.maxFrames = maxFrames; return this; }
        /** Enable/disable the debug UI overlay. Default: true. */
        public Builder debugOverlay(boolean debugOverlay) { this.debugOverlay = debugOverlay; return this; }
        public Builder platform(Platform platform) { this.platform = platform; return this; }
        /** Sets the graphics configuration (new API — preferred over graphicsBackend). */
        public Builder graphics(GraphicsConfig graphics) { this.graphics = graphics; return this; }
        /** @deprecated Use {@link #graphics(GraphicsConfig)} instead. */
        @Deprecated
        public Builder graphicsBackend(GraphicsBackendFactory graphicsBackend) { this.graphicsBackend = graphicsBackend; return this; }

        public EngineConfig build() {
            if (headless) {
                if (platform == null) platform = HeadlessPlatform.INSTANCE;
            }
            var windowDesc = window != null ? window
                    : new WindowDescriptor(windowTitle, windowWidth, windowHeight);
            return new EngineConfig(headless, threaded, windowDesc, scene, maxFrames, debugOverlay, platform, graphics, graphicsBackend);
        }
    }
}
