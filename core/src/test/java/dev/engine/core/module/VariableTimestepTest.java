package dev.engine.core.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VariableTimestepTest {

    private static final double DELTA = 1e-9;

    private static Consumer<Time> collectInto(List<Time> list) {
        return list::add;
    }

    @Test
    void firesExactlyOnce() {
        VariableTimestep<Time> strategy = new VariableTimestep<>(Time::new);
        List<Time> calls = new ArrayList<>();

        strategy.advance(0.016, collectInto(calls));

        assertEquals(1, calls.size());
    }

    @Test
    void passesElapsedTime() {
        VariableTimestep<Time> strategy = new VariableTimestep<>(Time::new);
        List<Time> received = new ArrayList<>();

        strategy.advance(0.033, received::add);

        assertEquals(1, received.size());
        assertEquals(0.033, received.get(0).timeDelta(), DELTA);
    }

    @Test
    void clampsNegative() {
        VariableTimestep<Time> strategy = new VariableTimestep<>(Time::new);
        List<Time> received = new ArrayList<>();

        strategy.advance(-5.0, received::add);

        assertEquals(1, received.size());
        assertEquals(0.0, received.get(0).timeDelta(), DELTA);
    }

    @Test
    void zeroElapsed() {
        VariableTimestep<Time> strategy = new VariableTimestep<>(Time::new);
        List<Time> received = new ArrayList<>();

        strategy.advance(0.0, received::add);

        assertEquals(1, received.size());
        assertEquals(0.0, received.get(0).timeDelta(), DELTA);
    }

    @Test
    void largeElapsed() {
        double large = 999_999.0;
        VariableTimestep<Time> strategy = new VariableTimestep<>(Time::new);
        List<Time> received = new ArrayList<>();

        strategy.advance(large, received::add);

        assertEquals(1, received.size());
        assertEquals(large, received.get(0).timeDelta(), DELTA);
    }

    @Test
    void constructorValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> new VariableTimestep<>(null));
    }
}
