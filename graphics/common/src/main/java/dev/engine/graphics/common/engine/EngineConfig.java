package dev.engine.graphics.common.engine;

import dev.engine.core.scene.AbstractScene;

/**
 * Configuration for the Engine. Determines threading mode, backend, window, scene type.
 */
public record EngineConfig(
        boolean headless,
        boolean threaded,
        String windowTitle,
        int windowWidth,
        int windowHeight,
        AbstractScene scene
) {
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean headless = false;
        private boolean threaded = false;
        private String windowTitle = "Engine";
        private int windowWidth = 1280;
        private int windowHeight = 720;
        private AbstractScene scene = null;

        public Builder headless(boolean headless) { this.headless = headless; return this; }
        public Builder threaded(boolean threaded) { this.threaded = threaded; return this; }
        public Builder windowTitle(String title) { this.windowTitle = title; return this; }
        public Builder windowSize(int w, int h) { this.windowWidth = w; this.windowHeight = h; return this; }
        public Builder scene(AbstractScene scene) { this.scene = scene; return this; }

        public EngineConfig build() {
            return new EngineConfig(headless, threaded, windowTitle, windowWidth, windowHeight, scene);
        }
    }
}
