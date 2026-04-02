package dev.engine.core.module;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Variable timestep {@link UpdateStrategy} that fires exactly one update per advance call.
 *
 * <p>The actual elapsed time (clamped to zero if negative) is passed to the context
 * factory and forwarded to the update callback.
 *
 * @param <T> the update context type passed to modules
 */
public class VariableTimestep<T extends Time> implements UpdateStrategy<T> {

    private static final Logger log = LoggerFactory.getLogger(VariableTimestep.class);

    private final BiFunction<Long, Double, T> contextFactory;
    private long frameCounter = 0;

    public VariableTimestep(BiFunction<Long, Double, T> contextFactory) {
        if (contextFactory == null) {
            throw new IllegalArgumentException("contextFactory must not be null");
        }
        this.contextFactory = contextFactory;
    }

    @Override
    public void advance(double elapsedSeconds, Consumer<T> onUpdate) {
        double clamped = Math.max(0.0, elapsedSeconds);
        onUpdate.accept(contextFactory.apply(frameCounter++, clamped));
        log.trace("VariableTimestep advanced: elapsed={}s", clamped);
    }
}
