package dev.engine.core.module;

import java.util.function.Consumer;

/**
 * Strategy that determines when and how module updates are fired.
 *
 * <p>Given elapsed wall-clock time, the strategy computes zero or more update
 * steps and invokes the callback for each step with an appropriate context value.
 *
 * @param <T> the update context type passed to modules
 */
@FunctionalInterface
public interface UpdateStrategy<T extends Time> {

    /**
     * Advances time by the given elapsed seconds and invokes {@code onUpdate}
     * for each computed update step.
     *
     * @param elapsedSeconds real wall-clock time elapsed since last advance
     * @param onUpdate       callback invoked with the context for each update step
     */
    void advance(double elapsedSeconds, Consumer<T> onUpdate);
}
