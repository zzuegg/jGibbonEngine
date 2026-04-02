package dev.engine.core.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    record TestEvent(String message) implements Event {}
    record OtherEvent(int value) implements Event {}

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = new EventBus();
    }

    @Nested
    class Subscribe {
        @Test
        void subscriberReceivesPublishedEvent() {
            var received = new ArrayList<TestEvent>();
            bus.subscribe(TestEvent.class, received::add);
            bus.publish(new TestEvent("hello"));
            assertEquals(1, received.size());
            assertEquals("hello", received.getFirst().message());
        }

        @Test
        void subscriberDoesNotReceiveUnrelatedEvents() {
            var received = new ArrayList<TestEvent>();
            bus.subscribe(TestEvent.class, received::add);
            bus.publish(new OtherEvent(42));
            assertTrue(received.isEmpty());
        }

        @Test
        void multipleSubscribersAllReceiveEvent() {
            var first = new ArrayList<TestEvent>();
            var second = new ArrayList<TestEvent>();
            bus.subscribe(TestEvent.class, first::add);
            bus.subscribe(TestEvent.class, second::add);
            bus.publish(new TestEvent("both"));
            assertEquals(1, first.size());
            assertEquals(1, second.size());
        }
    }

    @Nested
    class Unsubscribe {
        @Test
        void unsubscribedListenerStopsReceiving() {
            var received = new ArrayList<TestEvent>();
            var subscription = bus.subscribe(TestEvent.class, received::add);
            bus.publish(new TestEvent("before"));
            subscription.unsubscribe();
            bus.publish(new TestEvent("after"));
            assertEquals(1, received.size());
            assertEquals("before", received.getFirst().message());
        }

        @Test
        void doubleUnsubscribeIsHarmless() {
            var subscription = bus.subscribe(TestEvent.class, e -> {});
            subscription.unsubscribe();
            assertDoesNotThrow(subscription::unsubscribe);
        }
    }

    @Nested
    class ThreadSafety {
        @Test
        void concurrentPublishAndSubscribe() throws InterruptedException {
            var count = new AtomicInteger(0);
            int threads = 8;
            int eventsPerThread = 1000;
            var latch = new CountDownLatch(threads);

            bus.subscribe(TestEvent.class, e -> count.incrementAndGet());

            try (var executor = Executors.newFixedThreadPool(threads)) {
                for (int t = 0; t < threads; t++) {
                    executor.submit(() -> {
                        for (int i = 0; i < eventsPerThread; i++) {
                            bus.publish(new TestEvent("msg"));
                        }
                        latch.countDown();
                    });
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS));
            }
            assertEquals(threads * eventsPerThread, count.get());
        }
    }

    @Nested
    class ErrorHandling {
        @Test
        void failingSubscriberDoesNotBlockOthers() {
            var received = new ArrayList<TestEvent>();
            bus.subscribe(TestEvent.class, e -> { throw new RuntimeException("boom"); });
            bus.subscribe(TestEvent.class, received::add);
            bus.publish(new TestEvent("still works"));
            assertEquals(1, received.size());
        }
    }
}
