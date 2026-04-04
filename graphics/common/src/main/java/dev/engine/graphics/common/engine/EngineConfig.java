package dev.engine.graphics.common.engine;

import dev.engine.core.math.Vec2i;
import dev.engine.core.scene.AbstractScene;
import dev.engine.graphics.GraphicsBackendFactory;

/**
 * Configuration for the Engine. Determines threading mode, backend, window, scene type.
 *
 * <pre>{@code
 * var config = EngineConfig.builder()
 *     .windowTitle("My Game")
 *     .windowSize(1280, 720)
 *     .platform(DesktopPlatform.builder().build())
 *     .graphicsBackend(OpenGlBackend.factory(glBindings))
 *     .build();
 *
 * new MyGame().launch(config);
 * }</pre>
 */
public record EngineConfig(
        boolean headless,
        boolean threaded,
        String windowTitle,
        Vec2i windowSize,
        AbstractScene scene,
        int maxFrames,
        Platform platform,
        GraphicsBackendFactory graphicsBackend
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean headless = false;
        private boolean threaded = false;
        private String windowTitle = "Engine";
        private Vec2i windowSize = new Vec2i(1280, 720);
        private AbstractScene scene = null;
        private int maxFrames = 0; // 0 = unlimited
        private Platform platform = null;
        private GraphicsBackendFactory graphicsBackend = null;

        public Builder headless(boolean headless) { this.headless = headless; return this; }
        public Builder threaded(boolean threaded) { this.threaded = threaded; return this; }
        public Builder windowTitle(String title) { this.windowTitle = title; return this; }
        public Builder windowSize(Vec2i size) { this.windowSize = size; return this; }
        public Builder windowSize(int w, int h) { this.windowSize = new Vec2i(w, h); return this; }
        public Builder scene(AbstractScene scene) { this.scene = scene; return this; }
        public Builder maxFrames(int maxFrames) { this.maxFrames = maxFrames; return this; }
        public Builder platform(Platform platform) { this.platform = platform; return this; }
        public Builder graphicsBackend(GraphicsBackendFactory graphicsBackend) { this.graphicsBackend = graphicsBackend; return this; }

        public EngineConfig build() {
            if (headless) {
                if (platform == null) platform = HeadlessPlatform.INSTANCE;
            }
            return new EngineConfig(headless, threaded, windowTitle, windowSize, scene, maxFrames, platform, graphicsBackend);
        }
    }
}
