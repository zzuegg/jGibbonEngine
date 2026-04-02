package dev.engine.core.module;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Accumulator-based fixed timestep {@link UpdateStrategy}.
 *
 * <p>Accumulates elapsed time and fires a fixed number of update steps per tick,
 * each step using a constant delta-time value derived from the target frequency.
 * Excess accumulated time beyond the cap is discarded to prevent a spiral of death.
 *
 * @param <T> the update context type passed to modules
 */
public class FixedTimestep<T extends Time> implements UpdateStrategy<T> {

    private static final Logger log = LoggerFactory.getLogger(FixedTimestep.class);
    private static final int DEFAULT_MAX_STEPS_PER_TICK = 5;

    private final double fixedDt;
    private final double targetHz;
    private final BiFunction<Long, Double, T> contextFactory;
    private final int maxStepsPerTick;
    private double accumulator;
    private long frameCounter = 0;

    public FixedTimestep(double targetHz, BiFunction<Long, Double, T> contextFactory, int maxStepsPerTick) {
        if (targetHz <= 0) {
            throw new IllegalArgumentException("targetHz must be > 0, got: " + targetHz);
        }
        if (maxStepsPerTick <= 0) {
            throw new IllegalArgumentException("maxStepsPerTick must be > 0, got: " + maxStepsPerTick);
        }
        if (contextFactory == null) {
            throw new IllegalArgumentException("contextFactory must not be null");
        }
        this.targetHz = targetHz;
        this.fixedDt = 1.0 / targetHz;
        this.contextFactory = contextFactory;
        this.maxStepsPerTick = maxStepsPerTick;
        this.accumulator = 0.0;
    }

    public FixedTimestep(double targetHz, BiFunction<Long, Double, T> contextFactory) {
        this(targetHz, contextFactory, DEFAULT_MAX_STEPS_PER_TICK);
    }

    @Override
    public void advance(double elapsedSeconds, Consumer<T> onUpdate) {
        double clamped = Math.max(0.0, elapsedSeconds);
        accumulator += clamped;

        int steps = 0;
        while (accumulator >= fixedDt && steps < maxStepsPerTick) {
            onUpdate.accept(contextFactory.apply(frameCounter++, fixedDt));
            accumulator -= fixedDt;
            steps++;
        }

        if (accumulator > fixedDt) {
            accumulator = fixedDt;
        }

        log.trace("FixedTimestep advanced: {} steps, accumulator={}", steps, accumulator);
    }

    public double getFixedDt() {
        return fixedDt;
    }

    public double getTargetHz() {
        return targetHz;
    }

    public double getAccumulator() {
        return accumulator;
    }

    public int getMaxStepsPerTick() {
        return maxStepsPerTick;
    }
}
