package dev.engine.core.module;

import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op {@link UpdateStrategy} for modules whose updates are driven externally.
 *
 * <p>When this strategy is active, {@link #advance} does nothing. Updates are
 * expected to be triggered by the caller directly via
 * {@code ModuleManager.update(T)} rather than through the timed advance mechanism.
 *
 * @param <T> the update context type passed to modules
 */
public class ManualUpdate<T extends Time> implements UpdateStrategy<T> {

    private static final Logger log = LoggerFactory.getLogger(ManualUpdate.class);

    public ManualUpdate() {
    }

    @Override
    public void advance(double elapsedSeconds, Consumer<T> onUpdate) {
        log.trace("ManualUpdate advance called (no-op)");
    }
}
