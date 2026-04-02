package dev.engine.core.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FixedTimestepTest {

    private static final double TARGET_HZ = 60.0;
    private static final double FIXED_DT = 1.0 / TARGET_HZ;
    private static final double DELTA = 1e-9;

    private static Consumer<Time> collectInto(List<Time> list) {
        return list::add;
    }

    @Test
    void exactlyOnTarget() {
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new);
        List<Time> calls = new ArrayList<>();

        strategy.advance(FIXED_DT, collectInto(calls));

        assertEquals(1, calls.size());
    }

    @Test
    void belowTarget() {
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new);
        List<Time> calls = new ArrayList<>();

        strategy.advance(FIXED_DT * 0.5, collectInto(calls));

        assertEquals(0, calls.size());
    }

    @Test
    void multipleSteps() {
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new);
        List<Time> calls = new ArrayList<>();

        strategy.advance(FIXED_DT * 3, collectInto(calls));

        assertEquals(3, calls.size());
    }

    @Test
    void accumulatorRemainder() {
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new);
        List<Time> calls = new ArrayList<>();

        strategy.advance(FIXED_DT * 1.5, collectInto(calls));

        assertEquals(1, calls.size());
        assertEquals(FIXED_DT * 0.5, strategy.getAccumulator(), DELTA);
    }

    @Test
    void accumulatorCarryOver() {
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new);
        List<Time> firstCalls = new ArrayList<>();
        List<Time> secondCalls = new ArrayList<>();

        strategy.advance(FIXED_DT * 0.6, collectInto(firstCalls));
        strategy.advance(FIXED_DT * 0.6, collectInto(secondCalls));

        assertEquals(0, firstCalls.size());
        assertEquals(1, secondCalls.size());
    }

    @Test
    void spiralOfDeathPrevention() {
        int maxSteps = 3;
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new, maxSteps);
        List<Time> calls = new ArrayList<>();

        strategy.advance(FIXED_DT * 100, collectInto(calls));

        assertEquals(3, calls.size());
        assertEquals(FIXED_DT, strategy.getAccumulator(), DELTA);
    }

    @Test
    void contextFactoryCalledWithFixedDt() {
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new);
        List<Time> received = new ArrayList<>();

        strategy.advance(FIXED_DT * 3, received::add);

        assertEquals(3, received.size());
        for (Time value : received) {
            assertEquals(FIXED_DT, value.timeDelta(), DELTA);
        }
    }

    @Test
    void zeroElapsedTime() {
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new);
        List<Time> calls = new ArrayList<>();

        strategy.advance(0.0, collectInto(calls));

        assertEquals(0, calls.size());
    }

    @Test
    void negativeElapsedTime() {
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new);
        List<Time> calls = new ArrayList<>();

        strategy.advance(-1.0, collectInto(calls));

        assertEquals(0, calls.size());
    }

    @Test
    void gettersReturnCorrectValues() {
        int maxSteps = 7;
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new, maxSteps);

        assertEquals(FIXED_DT, strategy.getFixedDt(), DELTA);
        assertEquals(TARGET_HZ, strategy.getTargetHz(), DELTA);
        assertEquals(maxSteps, strategy.getMaxStepsPerTick());
        assertEquals(0.0, strategy.getAccumulator(), DELTA);
    }

    @Test
    void defaultMaxStepsPerTick() {
        FixedTimestep<Time> strategy = new FixedTimestep<>(TARGET_HZ, Time::new);

        assertEquals(5, strategy.getMaxStepsPerTick());
    }

    @Test
    void constructorValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> new FixedTimestep<>(0.0, Time::new));

        assertThrows(IllegalArgumentException.class,
            () -> new FixedTimestep<>(-1.0, Time::new));

        assertThrows(IllegalArgumentException.class,
            () -> new FixedTimestep<>(TARGET_HZ, Time::new, 0));

        assertThrows(IllegalArgumentException.class,
            () -> new FixedTimestep<>(TARGET_HZ, Time::new, -1));

        assertThrows(IllegalArgumentException.class,
            () -> new FixedTimestep<>(TARGET_HZ, null));

        assertThrows(IllegalArgumentException.class,
            () -> new FixedTimestep<>(TARGET_HZ, null, 5));
    }
}
