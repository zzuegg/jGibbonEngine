package dev.engine.core.module;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManualUpdateTest {

    private static Consumer<Time> collectInto(List<Time> list) {
        return list::add;
    }

    @Test
    void advanceNeverCallsOnUpdate() {
        ManualUpdate<Time> strategy = new ManualUpdate<>();
        List<Time> calls = new ArrayList<>();

        strategy.advance(0.016, collectInto(calls));

        assertEquals(0, calls.size());
    }

    @Test
    void advanceWithZero() {
        ManualUpdate<Time> strategy = new ManualUpdate<>();
        List<Time> calls = new ArrayList<>();

        strategy.advance(0.0, collectInto(calls));

        assertEquals(0, calls.size());
    }

    @Test
    void advanceWithLarge() {
        ManualUpdate<Time> strategy = new ManualUpdate<>();
        List<Time> calls = new ArrayList<>();

        strategy.advance(1000.0, collectInto(calls));

        assertEquals(0, calls.size());
    }
}
