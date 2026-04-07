package dev.engine.tests.screenshot.scenes;

import dev.engine.core.input.InputEvent;
import dev.engine.graphics.common.engine.Engine;

import java.util.List;
import java.util.Map;

/**
 * Defines a visual test scene. Implement {@link #setup(Engine)} to create
 * entities, cameras, materials, and optionally register modules for animation.
 *
 * <p>To create a new test, add a {@code static final RenderTestScene} field
 * to any class in a sub-package of {@code scenes}. It will be discovered
 * automatically by {@link SceneRegistry}.
 */
@FunctionalInterface
public interface RenderTestScene {

    /** Sets up the scene using the full engine. */
    void setup(Engine engine);

    /** Scene-specific configuration. The runner merges platform/backend on top. */
    default SceneConfig config() {
        return SceneConfig.defaults();
    }

    /**
     * Input events to inject at specific frames. The runner pushes these into
     * the engine's input queue before ticking the frame.
     */
    default Map<Integer, List<InputEvent>> inputScript() {
        return Map.of();
    }
}
