package dev.engine.core.input;

import dev.engine.core.module.Time;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

class InputEventQueueTest {

    static final Time TIME = new Time(1, 0.016);
    static final DeviceId KB = new DeviceId(DeviceType.KEYBOARD, 0);
    static final DeviceId MOUSE = new DeviceId(DeviceType.MOUSE, 0);

    @Test void drainInputReturnsEventsInOrder() {
        var queue = new InputEventQueue();
        queue.pushInput(new InputEvent.KeyPressed(TIME, KB, KeyCode.A, new ScanCode(30), new Modifiers(0)));
        queue.pushInput(new InputEvent.KeyReleased(TIME, KB, KeyCode.A, new ScanCode(30), new Modifiers(0)));

        var events = queue.drainInput();
        assertEquals(2, events.size());
        assertInstanceOf(InputEvent.KeyPressed.class, events.get(0));
        assertInstanceOf(InputEvent.KeyReleased.class, events.get(1));
    }

    @Test void drainClearsQueue() {
        var queue = new InputEventQueue();
        queue.pushInput(new InputEvent.KeyPressed(TIME, KB, KeyCode.A, new ScanCode(30), new Modifiers(0)));
        queue.drainInput();
        var events = queue.drainInput();
        assertTrue(events.isEmpty());
    }

    @Test void drainWindowReturnsEventsInOrder() {
        var queue = new InputEventQueue();
        queue.pushWindow(new WindowEvent.Resized(TIME, 1920, 1080));
        queue.pushWindow(new WindowEvent.FocusGained(TIME));

        var events = queue.drainWindow();
        assertEquals(2, events.size());
        assertInstanceOf(WindowEvent.Resized.class, events.get(0));
        assertInstanceOf(WindowEvent.FocusGained.class, events.get(1));
    }

    @Test void emptyDrainReturnsEmptyList() {
        var queue = new InputEventQueue();
        assertTrue(queue.drainInput().isEmpty());
        assertTrue(queue.drainWindow().isEmpty());
    }

    @Test void returnedListIsImmutable() {
        var queue = new InputEventQueue();
        queue.pushInput(new InputEvent.KeyPressed(TIME, KB, KeyCode.A, new ScanCode(30), new Modifiers(0)));
        var events = queue.drainInput();
        assertThrows(UnsupportedOperationException.class, () ->
                events.add(new InputEvent.KeyPressed(TIME, KB, KeyCode.B, new ScanCode(48), new Modifiers(0))));
    }

    @Test void threadSafetyPushAndDrain() throws InterruptedException {
        var queue = new InputEventQueue();
        int pushCount = 1000;
        var latch = new CountDownLatch(2);
        var collected = new ArrayList<InputEvent>();

        // Producer thread
        var producer = new Thread(() -> {
            for (int i = 0; i < pushCount; i++) {
                queue.pushInput(new InputEvent.KeyPressed(TIME, KB, KeyCode.A, new ScanCode(30), new Modifiers(0)));
            }
            latch.countDown();
        });

        // Consumer thread
        var consumer = new Thread(() -> {
            int total = 0;
            while (total < pushCount) {
                var events = queue.drainInput();
                collected.addAll(events);
                total = collected.size();
                if (total < pushCount) Thread.yield();
            }
            latch.countDown();
        });

        producer.start();
        consumer.start();
        latch.await();

        assertEquals(pushCount, collected.size());
    }
}
