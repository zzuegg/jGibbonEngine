# Phase 1: Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the foundational infrastructure that every other engine subsystem depends on — event system, math types, native resource tracking, and property maps.

**Architecture:** All foundation code lives in the `core` module under `dev.engine.core.*`. Pure Java, zero external dependencies beyond SLF4J. Every type is designed for multithreading from the start. Math types are records (Valhalla-ready). No enums for extensible concepts.

**Tech Stack:** Java 25, JUnit 5, SLF4J, Gradle 9.4.1

---

## File Structure

### Event System (`dev.engine.core.event`)
- `core/src/main/java/dev/engine/core/event/Event.java` — marker interface for all events
- `core/src/main/java/dev/engine/core/event/EventBus.java` — thread-safe pub/sub event bus
- `core/src/test/java/dev/engine/core/event/EventBusTest.java` — tests

### Math Types (`dev.engine.core.math`)
- `core/src/main/java/dev/engine/core/math/Vec2.java` — 2D vector record
- `core/src/main/java/dev/engine/core/math/Vec3.java` — 3D vector record
- `core/src/main/java/dev/engine/core/math/Vec4.java` — 4D vector record
- `core/src/main/java/dev/engine/core/math/Mat4.java` — 4x4 matrix record
- `core/src/main/java/dev/engine/core/math/Quat.java` — quaternion record
- `core/src/test/java/dev/engine/core/math/Vec2Test.java`
- `core/src/test/java/dev/engine/core/math/Vec3Test.java`
- `core/src/test/java/dev/engine/core/math/Vec4Test.java`
- `core/src/test/java/dev/engine/core/math/Mat4Test.java`
- `core/src/test/java/dev/engine/core/math/QuatTest.java`

### Native Resource Tracking (`dev.engine.core.resource`)
- `core/src/main/java/dev/engine/core/resource/NativeResource.java` — AutoCloseable base with Cleaner integration
- `core/src/main/java/dev/engine/core/resource/ResourceCleaner.java` — shared Cleaner instance + release queue
- `core/src/test/java/dev/engine/core/resource/NativeResourceTest.java`

### Property Map (`dev.engine.core.property`)
- `core/src/main/java/dev/engine/core/property/PropertyKey.java` — typed key for property lookup
- `core/src/main/java/dev/engine/core/property/PropertyMap.java` — immutable typed property bag
- `core/src/main/java/dev/engine/core/property/MutablePropertyMap.java` — mutable builder/variant with change tracking
- `core/src/test/java/dev/engine/core/property/PropertyMapTest.java`

### Handles (`dev.engine.core.handle`)
- `core/src/main/java/dev/engine/core/handle/Handle.java` — generational opaque handle
- `core/src/main/java/dev/engine/core/handle/HandlePool.java` — allocator/validator for handles
- `core/src/test/java/dev/engine/core/handle/HandlePoolTest.java`

---

## Task 1: Event System

**Files:**
- Create: `core/src/main/java/dev/engine/core/event/Event.java`
- Create: `core/src/main/java/dev/engine/core/event/EventBus.java`
- Create: `core/src/test/java/dev/engine/core/event/EventBusTest.java`

- [ ] **Step 1: Write Event marker interface**

```java
package dev.engine.core.event;

public interface Event {}
```

- [ ] **Step 2: Write failing tests for EventBus**

```java
package dev.engine.core.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "dev.engine.core.event.EventBusTest" --console=plain`
Expected: FAIL — EventBus does not exist yet

- [ ] **Step 4: Implement EventBus**

```java
package dev.engine.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <E extends Event> Subscription subscribe(Class<E> eventType, Consumer<E> listener) {
        var list = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        return new Subscription(() -> list.remove(listener));
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> void publish(E event) {
        var list = listeners.get(event.getClass());
        if (list == null) return;
        for (var listener : list) {
            try {
                ((Consumer<E>) listener).accept(event);
            } catch (Exception e) {
                log.warn("Event listener threw exception for {}: {}",
                        event.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    public static class Subscription {
        private final Runnable unsubscribeAction;
        private volatile boolean active = true;

        Subscription(Runnable unsubscribeAction) {
            this.unsubscribeAction = unsubscribeAction;
        }

        public void unsubscribe() {
            if (active) {
                active = false;
                unsubscribeAction.run();
            }
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "dev.engine.core.event.EventBusTest" --console=plain`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/dev/engine/core/event/ core/src/test/java/dev/engine/core/event/
git commit -m "feat(core): add thread-safe EventBus with typed pub/sub"
```

---

## Task 2: Math — Vec2

**Files:**
- Create: `core/src/main/java/dev/engine/core/math/Vec2.java`
- Create: `core/src/test/java/dev/engine/core/math/Vec2Test.java`

- [ ] **Step 1: Write failing tests**

```java
package dev.engine.core.math;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Vec2Test {

    static final float EPSILON = 1e-6f;

    @Nested
    class Construction {
        @Test void components() {
            var v = new Vec2(1f, 2f);
            assertEquals(1f, v.x());
            assertEquals(2f, v.y());
        }
        @Test void zero() {
            assertEquals(new Vec2(0f, 0f), Vec2.ZERO);
        }
        @Test void one() {
            assertEquals(new Vec2(1f, 1f), Vec2.ONE);
        }
        @Test void unitX() {
            assertEquals(new Vec2(1f, 0f), Vec2.UNIT_X);
        }
        @Test void unitY() {
            assertEquals(new Vec2(0f, 1f), Vec2.UNIT_Y);
        }
    }

    @Nested
    class Arithmetic {
        @Test void add() {
            assertEquals(new Vec2(3f, 5f), new Vec2(1f, 2f).add(new Vec2(2f, 3f)));
        }
        @Test void sub() {
            assertEquals(new Vec2(-1f, -1f), new Vec2(1f, 2f).sub(new Vec2(2f, 3f)));
        }
        @Test void mul() {
            assertEquals(new Vec2(2f, 6f), new Vec2(1f, 2f).mul(new Vec2(2f, 3f)));
        }
        @Test void scale() {
            assertEquals(new Vec2(3f, 6f), new Vec2(1f, 2f).scale(3f));
        }
        @Test void negate() {
            assertEquals(new Vec2(-1f, -2f), new Vec2(1f, 2f).negate());
        }
    }

    @Nested
    class VectorOps {
        @Test void dot() {
            assertEquals(11f, new Vec2(1f, 2f).dot(new Vec2(3f, 4f)), EPSILON);
        }
        @Test void length() {
            assertEquals(5f, new Vec2(3f, 4f).length(), EPSILON);
        }
        @Test void lengthSquared() {
            assertEquals(25f, new Vec2(3f, 4f).lengthSquared(), EPSILON);
        }
        @Test void normalize() {
            var n = new Vec2(3f, 4f).normalize();
            assertEquals(1f, n.length(), EPSILON);
            assertEquals(0.6f, n.x(), EPSILON);
            assertEquals(0.8f, n.y(), EPSILON);
        }
        @Test void lerp() {
            var result = new Vec2(0f, 0f).lerp(new Vec2(10f, 10f), 0.5f);
            assertEquals(new Vec2(5f, 5f), result);
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "dev.engine.core.math.Vec2Test" --console=plain`
Expected: FAIL

- [ ] **Step 3: Implement Vec2**

```java
package dev.engine.core.math;

public record Vec2(float x, float y) {

    public static final Vec2 ZERO = new Vec2(0f, 0f);
    public static final Vec2 ONE = new Vec2(1f, 1f);
    public static final Vec2 UNIT_X = new Vec2(1f, 0f);
    public static final Vec2 UNIT_Y = new Vec2(0f, 1f);

    public Vec2 add(Vec2 other) { return new Vec2(x + other.x, y + other.y); }
    public Vec2 sub(Vec2 other) { return new Vec2(x - other.x, y - other.y); }
    public Vec2 mul(Vec2 other) { return new Vec2(x * other.x, y * other.y); }
    public Vec2 scale(float s) { return new Vec2(x * s, y * s); }
    public Vec2 negate() { return new Vec2(-x, -y); }
    public float dot(Vec2 other) { return x * other.x + y * other.y; }
    public float lengthSquared() { return x * x + y * y; }
    public float length() { return (float) Math.sqrt(lengthSquared()); }
    public Vec2 normalize() { float len = length(); return new Vec2(x / len, y / len); }
    public Vec2 lerp(Vec2 other, float t) { return new Vec2(x + (other.x - x) * t, y + (other.y - y) * t); }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "dev.engine.core.math.Vec2Test" --console=plain`
Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/dev/engine/core/math/Vec2.java core/src/test/java/dev/engine/core/math/Vec2Test.java
git commit -m "feat(core): add Vec2 record type with arithmetic and vector ops"
```

---

## Task 3: Math — Vec3

**Files:**
- Create: `core/src/main/java/dev/engine/core/math/Vec3.java`
- Create: `core/src/test/java/dev/engine/core/math/Vec3Test.java`

- [ ] **Step 1: Write failing tests**

```java
package dev.engine.core.math;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Vec3Test {

    static final float EPSILON = 1e-6f;

    @Nested
    class Construction {
        @Test void components() {
            var v = new Vec3(1f, 2f, 3f);
            assertEquals(1f, v.x()); assertEquals(2f, v.y()); assertEquals(3f, v.z());
        }
        @Test void zero() { assertEquals(new Vec3(0f, 0f, 0f), Vec3.ZERO); }
        @Test void one() { assertEquals(new Vec3(1f, 1f, 1f), Vec3.ONE); }
        @Test void unitX() { assertEquals(new Vec3(1f, 0f, 0f), Vec3.UNIT_X); }
        @Test void unitY() { assertEquals(new Vec3(0f, 1f, 0f), Vec3.UNIT_Y); }
        @Test void unitZ() { assertEquals(new Vec3(0f, 0f, 1f), Vec3.UNIT_Z); }
    }

    @Nested
    class Arithmetic {
        @Test void add() { assertEquals(new Vec3(3f, 5f, 7f), new Vec3(1f, 2f, 3f).add(new Vec3(2f, 3f, 4f))); }
        @Test void sub() { assertEquals(new Vec3(-1f, -1f, -1f), new Vec3(1f, 2f, 3f).sub(new Vec3(2f, 3f, 4f))); }
        @Test void mul() { assertEquals(new Vec3(2f, 6f, 12f), new Vec3(1f, 2f, 3f).mul(new Vec3(2f, 3f, 4f))); }
        @Test void scale() { assertEquals(new Vec3(2f, 4f, 6f), new Vec3(1f, 2f, 3f).scale(2f)); }
        @Test void negate() { assertEquals(new Vec3(-1f, -2f, -3f), new Vec3(1f, 2f, 3f).negate()); }
    }

    @Nested
    class VectorOps {
        @Test void dot() { assertEquals(32f, new Vec3(1f, 2f, 3f).dot(new Vec3(4f, 5f, 6f)), EPSILON); }
        @Test void cross() {
            var result = new Vec3(1f, 0f, 0f).cross(new Vec3(0f, 1f, 0f));
            assertEquals(0f, result.x(), EPSILON);
            assertEquals(0f, result.y(), EPSILON);
            assertEquals(1f, result.z(), EPSILON);
        }
        @Test void crossAnticommutative() {
            var a = new Vec3(1f, 2f, 3f);
            var b = new Vec3(4f, 5f, 6f);
            assertEquals(a.cross(b).negate(), b.cross(a));
        }
        @Test void length() { assertEquals(5f, new Vec3(3f, 4f, 0f).length(), EPSILON); }
        @Test void lengthSquared() { assertEquals(14f, new Vec3(1f, 2f, 3f).lengthSquared(), EPSILON); }
        @Test void normalize() {
            var n = new Vec3(0f, 3f, 4f).normalize();
            assertEquals(1f, n.length(), EPSILON);
        }
        @Test void lerp() {
            assertEquals(new Vec3(5f, 5f, 5f),
                    new Vec3(0f, 0f, 0f).lerp(new Vec3(10f, 10f, 10f), 0.5f));
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement Vec3**

```java
package dev.engine.core.math;

public record Vec3(float x, float y, float z) {

    public static final Vec3 ZERO = new Vec3(0f, 0f, 0f);
    public static final Vec3 ONE = new Vec3(1f, 1f, 1f);
    public static final Vec3 UNIT_X = new Vec3(1f, 0f, 0f);
    public static final Vec3 UNIT_Y = new Vec3(0f, 1f, 0f);
    public static final Vec3 UNIT_Z = new Vec3(0f, 0f, 1f);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 mul(Vec3 o) { return new Vec3(x * o.x, y * o.y, z * o.z); }
    public Vec3 scale(float s) { return new Vec3(x * s, y * s, z * s); }
    public Vec3 negate() { return new Vec3(-x, -y, -z); }
    public float dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
    public Vec3 cross(Vec3 o) { return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public float lengthSquared() { return x * x + y * y + z * z; }
    public float length() { return (float) Math.sqrt(lengthSquared()); }
    public Vec3 normalize() { float len = length(); return new Vec3(x / len, y / len, z / len); }
    public Vec3 lerp(Vec3 o, float t) { return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t); }
}
```

- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

---

## Task 4: Math — Vec4

**Files:**
- Create: `core/src/main/java/dev/engine/core/math/Vec4.java`
- Create: `core/src/test/java/dev/engine/core/math/Vec4Test.java`

- [ ] **Step 1: Write failing tests**

```java
package dev.engine.core.math;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Vec4Test {

    static final float EPSILON = 1e-6f;

    @Nested
    class Construction {
        @Test void components() {
            var v = new Vec4(1f, 2f, 3f, 4f);
            assertEquals(1f, v.x()); assertEquals(2f, v.y());
            assertEquals(3f, v.z()); assertEquals(4f, v.w());
        }
        @Test void zero() { assertEquals(new Vec4(0f, 0f, 0f, 0f), Vec4.ZERO); }
        @Test void one() { assertEquals(new Vec4(1f, 1f, 1f, 1f), Vec4.ONE); }
        @Test void fromVec3AndW() {
            assertEquals(new Vec4(1f, 2f, 3f, 1f), Vec4.of(new Vec3(1f, 2f, 3f), 1f));
        }
    }

    @Nested
    class Arithmetic {
        @Test void add() { assertEquals(new Vec4(3f, 5f, 7f, 9f), new Vec4(1f, 2f, 3f, 4f).add(new Vec4(2f, 3f, 4f, 5f))); }
        @Test void sub() { assertEquals(new Vec4(-1f, -1f, -1f, -1f), new Vec4(1f, 2f, 3f, 4f).sub(new Vec4(2f, 3f, 4f, 5f))); }
        @Test void scale() { assertEquals(new Vec4(2f, 4f, 6f, 8f), new Vec4(1f, 2f, 3f, 4f).scale(2f)); }
        @Test void negate() { assertEquals(new Vec4(-1f, -2f, -3f, -4f), new Vec4(1f, 2f, 3f, 4f).negate()); }
    }

    @Nested
    class VectorOps {
        @Test void dot() { assertEquals(70f, new Vec4(1f, 2f, 3f, 4f).dot(new Vec4(4f, 5f, 6f, 7f)), EPSILON); }
        @Test void length() { assertEquals(2f, new Vec4(1f, 1f, 1f, 1f).length(), EPSILON); }
        @Test void normalize() { assertEquals(1f, new Vec4(1f, 2f, 3f, 4f).normalize().length(), EPSILON); }
        @Test void xyz() { assertEquals(new Vec3(1f, 2f, 3f), new Vec4(1f, 2f, 3f, 4f).xyz()); }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement Vec4**

```java
package dev.engine.core.math;

public record Vec4(float x, float y, float z, float w) {

    public static final Vec4 ZERO = new Vec4(0f, 0f, 0f, 0f);
    public static final Vec4 ONE = new Vec4(1f, 1f, 1f, 1f);

    public static Vec4 of(Vec3 v, float w) { return new Vec4(v.x(), v.y(), v.z(), w); }

    public Vec4 add(Vec4 o) { return new Vec4(x + o.x, y + o.y, z + o.z, w + o.w); }
    public Vec4 sub(Vec4 o) { return new Vec4(x - o.x, y - o.y, z - o.z, w - o.w); }
    public Vec4 scale(float s) { return new Vec4(x * s, y * s, z * s, w * s); }
    public Vec4 negate() { return new Vec4(-x, -y, -z, -w); }
    public float dot(Vec4 o) { return x * o.x + y * o.y + z * o.z + w * o.w; }
    public float lengthSquared() { return x * x + y * y + z * z + w * w; }
    public float length() { return (float) Math.sqrt(lengthSquared()); }
    public Vec4 normalize() { float len = length(); return new Vec4(x / len, y / len, z / len, w / len); }
    public Vec3 xyz() { return new Vec3(x, y, z); }
}
```

- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

---

## Task 5: Math — Mat4

**Files:**
- Create: `core/src/main/java/dev/engine/core/math/Mat4.java`
- Create: `core/src/test/java/dev/engine/core/math/Mat4Test.java`

- [ ] **Step 1: Write failing tests**

```java
package dev.engine.core.math;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Mat4Test {

    static final float EPSILON = 1e-5f;

    @Nested
    class Identity {
        @Test void identityDiagonal() {
            var m = Mat4.IDENTITY;
            assertEquals(1f, m.m00()); assertEquals(1f, m.m11());
            assertEquals(1f, m.m22()); assertEquals(1f, m.m33());
        }
        @Test void identityTimesVec() {
            var v = new Vec4(1f, 2f, 3f, 1f);
            assertEquals(v, Mat4.IDENTITY.transform(v));
        }
        @Test void identityTimesIdentity() {
            assertEquals(Mat4.IDENTITY, Mat4.IDENTITY.mul(Mat4.IDENTITY));
        }
    }

    @Nested
    class Translation {
        @Test void translatePoint() {
            var m = Mat4.translation(1f, 2f, 3f);
            var result = m.transform(new Vec4(0f, 0f, 0f, 1f));
            assertEquals(1f, result.x(), EPSILON);
            assertEquals(2f, result.y(), EPSILON);
            assertEquals(3f, result.z(), EPSILON);
        }
        @Test void translateDoesNotAffectDirections() {
            var m = Mat4.translation(10f, 20f, 30f);
            var dir = new Vec4(1f, 0f, 0f, 0f);
            assertEquals(dir, m.transform(dir));
        }
    }

    @Nested
    class Scaling {
        @Test void scalePoint() {
            var m = Mat4.scaling(2f, 3f, 4f);
            var result = m.transform(new Vec4(1f, 1f, 1f, 1f));
            assertEquals(2f, result.x(), EPSILON);
            assertEquals(3f, result.y(), EPSILON);
            assertEquals(4f, result.z(), EPSILON);
        }
    }

    @Nested
    class Rotation {
        @Test void rotateXBy90() {
            var m = Mat4.rotationX((float) Math.toRadians(90));
            var result = m.transform(new Vec4(0f, 1f, 0f, 1f));
            assertEquals(0f, result.x(), EPSILON);
            assertEquals(0f, result.y(), EPSILON);
            assertEquals(1f, result.z(), EPSILON);
        }
        @Test void rotateYBy90() {
            var m = Mat4.rotationY((float) Math.toRadians(90));
            var result = m.transform(new Vec4(1f, 0f, 0f, 1f));
            assertEquals(0f, result.x(), EPSILON);
            assertEquals(0f, result.y(), EPSILON);
            assertEquals(-1f, result.z(), EPSILON);
        }
        @Test void rotateZBy90() {
            var m = Mat4.rotationZ((float) Math.toRadians(90));
            var result = m.transform(new Vec4(1f, 0f, 0f, 1f));
            assertEquals(0f, result.x(), EPSILON);
            assertEquals(1f, result.y(), EPSILON);
            assertEquals(0f, result.z(), EPSILON);
        }
    }

    @Nested
    class Multiplication {
        @Test void translateThenScale() {
            var t = Mat4.translation(1f, 0f, 0f);
            var s = Mat4.scaling(2f, 2f, 2f);
            // Scale first, then translate: T * S * v
            var m = t.mul(s);
            var result = m.transform(new Vec4(1f, 0f, 0f, 1f));
            assertEquals(3f, result.x(), EPSILON); // 1*2 + 1 = 3
            assertEquals(0f, result.y(), EPSILON);
            assertEquals(0f, result.z(), EPSILON);
        }
    }

    @Nested
    class Transpose {
        @Test void transposeIdentity() {
            assertEquals(Mat4.IDENTITY, Mat4.IDENTITY.transpose());
        }
        @Test void transposeSwapsOffDiagonal() {
            var m = Mat4.translation(1f, 2f, 3f);
            var mt = m.transpose();
            assertEquals(m.m03(), mt.m30());
            assertEquals(m.m13(), mt.m31());
            assertEquals(m.m23(), mt.m32());
        }
    }

    @Nested
    class Perspective {
        @Test void perspectiveProducesFiniteValues() {
            var m = Mat4.perspective((float) Math.toRadians(60), 16f / 9f, 0.1f, 100f);
            var result = m.transform(new Vec4(0f, 0f, -1f, 1f));
            assertTrue(Float.isFinite(result.x()));
            assertTrue(Float.isFinite(result.y()));
            assertTrue(Float.isFinite(result.z()));
            assertTrue(Float.isFinite(result.w()));
        }
    }

    @Nested
    class LookAt {
        @Test void lookAtForward() {
            var m = Mat4.lookAt(new Vec3(0f, 0f, 0f), new Vec3(0f, 0f, -1f), Vec3.UNIT_Y);
            // Camera looking down -Z: a point at (0,0,-5) should end up in front
            var result = m.transform(new Vec4(0f, 0f, -5f, 1f));
            assertEquals(0f, result.x(), EPSILON);
            assertEquals(0f, result.y(), EPSILON);
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement Mat4**

```java
package dev.engine.core.math;

public record Mat4(
        float m00, float m01, float m02, float m03,
        float m10, float m11, float m12, float m13,
        float m20, float m21, float m22, float m23,
        float m30, float m31, float m32, float m33
) {

    public static final Mat4 IDENTITY = new Mat4(
            1, 0, 0, 0,
            0, 1, 0, 0,
            0, 0, 1, 0,
            0, 0, 0, 1
    );

    public static Mat4 translation(float tx, float ty, float tz) {
        return new Mat4(
                1, 0, 0, tx,
                0, 1, 0, ty,
                0, 0, 1, tz,
                0, 0, 0, 1
        );
    }

    public static Mat4 translation(Vec3 t) { return translation(t.x(), t.y(), t.z()); }

    public static Mat4 scaling(float sx, float sy, float sz) {
        return new Mat4(
                sx, 0, 0, 0,
                0, sy, 0, 0,
                0, 0, sz, 0,
                0, 0, 0, 1
        );
    }

    public static Mat4 rotationX(float radians) {
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        return new Mat4(
                1, 0, 0, 0,
                0, c, -s, 0,
                0, s, c, 0,
                0, 0, 0, 1
        );
    }

    public static Mat4 rotationY(float radians) {
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        return new Mat4(
                c, 0, s, 0,
                0, 1, 0, 0,
                -s, 0, c, 0,
                0, 0, 0, 1
        );
    }

    public static Mat4 rotationZ(float radians) {
        float c = (float) Math.cos(radians), s = (float) Math.sin(radians);
        return new Mat4(
                c, -s, 0, 0,
                s, c, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1
        );
    }

    public static Mat4 perspective(float fovY, float aspect, float near, float far) {
        float tanHalfFov = (float) Math.tan(fovY * 0.5f);
        float range = near - far;
        return new Mat4(
                1f / (aspect * tanHalfFov), 0, 0, 0,
                0, 1f / tanHalfFov, 0, 0,
                0, 0, (far + near) / range, 2f * far * near / range,
                0, 0, -1, 0
        );
    }

    public static Mat4 lookAt(Vec3 eye, Vec3 center, Vec3 up) {
        Vec3 f = center.sub(eye).normalize();
        Vec3 s = f.cross(up).normalize();
        Vec3 u = s.cross(f);
        return new Mat4(
                s.x(), s.y(), s.z(), -s.dot(eye),
                u.x(), u.y(), u.z(), -u.dot(eye),
                -f.x(), -f.y(), -f.z(), f.dot(eye),
                0, 0, 0, 1
        );
    }

    public Mat4 mul(Mat4 o) {
        return new Mat4(
                m00*o.m00 + m01*o.m10 + m02*o.m20 + m03*o.m30,
                m00*o.m01 + m01*o.m11 + m02*o.m21 + m03*o.m31,
                m00*o.m02 + m01*o.m12 + m02*o.m22 + m03*o.m32,
                m00*o.m03 + m01*o.m13 + m02*o.m23 + m03*o.m33,

                m10*o.m00 + m11*o.m10 + m12*o.m20 + m13*o.m30,
                m10*o.m01 + m11*o.m11 + m12*o.m21 + m13*o.m31,
                m10*o.m02 + m11*o.m12 + m12*o.m22 + m13*o.m32,
                m10*o.m03 + m11*o.m13 + m12*o.m23 + m13*o.m33,

                m20*o.m00 + m21*o.m10 + m22*o.m20 + m23*o.m30,
                m20*o.m01 + m21*o.m11 + m22*o.m21 + m23*o.m31,
                m20*o.m02 + m21*o.m12 + m22*o.m22 + m23*o.m32,
                m20*o.m03 + m21*o.m13 + m22*o.m23 + m23*o.m33,

                m30*o.m00 + m31*o.m10 + m32*o.m20 + m33*o.m30,
                m30*o.m01 + m31*o.m11 + m32*o.m21 + m33*o.m31,
                m30*o.m02 + m31*o.m12 + m32*o.m22 + m33*o.m32,
                m30*o.m03 + m31*o.m13 + m32*o.m23 + m33*o.m33
        );
    }

    public Vec4 transform(Vec4 v) {
        return new Vec4(
                m00*v.x() + m01*v.y() + m02*v.z() + m03*v.w(),
                m10*v.x() + m11*v.y() + m12*v.z() + m13*v.w(),
                m20*v.x() + m21*v.y() + m22*v.z() + m23*v.w(),
                m30*v.x() + m31*v.y() + m32*v.z() + m33*v.w()
        );
    }

    public Mat4 transpose() {
        return new Mat4(
                m00, m10, m20, m30,
                m01, m11, m21, m31,
                m02, m12, m22, m32,
                m03, m13, m23, m33
        );
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

---

## Task 6: Math — Quat

**Files:**
- Create: `core/src/main/java/dev/engine/core/math/Quat.java`
- Create: `core/src/test/java/dev/engine/core/math/QuatTest.java`

- [ ] **Step 1: Write failing tests**

```java
package dev.engine.core.math;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuatTest {

    static final float EPSILON = 1e-5f;

    @Nested
    class Identity {
        @Test void identityRotatesNothing() {
            var v = new Vec3(1f, 2f, 3f);
            var result = Quat.IDENTITY.rotate(v);
            assertEquals(v.x(), result.x(), EPSILON);
            assertEquals(v.y(), result.y(), EPSILON);
            assertEquals(v.z(), result.z(), EPSILON);
        }
        @Test void identityLength() {
            assertEquals(1f, Quat.IDENTITY.length(), EPSILON);
        }
    }

    @Nested
    class AxisAngle {
        @Test void rotate90AroundY() {
            var q = Quat.fromAxisAngle(Vec3.UNIT_Y, (float) Math.toRadians(90));
            var result = q.rotate(new Vec3(1f, 0f, 0f));
            assertEquals(0f, result.x(), EPSILON);
            assertEquals(0f, result.y(), EPSILON);
            assertEquals(-1f, result.z(), EPSILON);
        }
        @Test void rotate180AroundZ() {
            var q = Quat.fromAxisAngle(Vec3.UNIT_Z, (float) Math.toRadians(180));
            var result = q.rotate(new Vec3(1f, 0f, 0f));
            assertEquals(-1f, result.x(), EPSILON);
            assertEquals(0f, result.y(), EPSILON);
            assertEquals(0f, result.z(), EPSILON);
        }
    }

    @Nested
    class Operations {
        @Test void multiplyIdentity() {
            var q = Quat.fromAxisAngle(Vec3.UNIT_Y, 0.5f);
            var result = q.mul(Quat.IDENTITY);
            assertEquals(q.x(), result.x(), EPSILON);
            assertEquals(q.y(), result.y(), EPSILON);
            assertEquals(q.z(), result.z(), EPSILON);
            assertEquals(q.w(), result.w(), EPSILON);
        }
        @Test void normalizePreservesRotation() {
            var q = Quat.fromAxisAngle(Vec3.UNIT_X, (float) Math.toRadians(45));
            var scaled = new Quat(q.x() * 2, q.y() * 2, q.z() * 2, q.w() * 2);
            var normalized = scaled.normalize();
            assertEquals(1f, normalized.length(), EPSILON);
            var v = new Vec3(0f, 1f, 0f);
            var r1 = q.rotate(v);
            var r2 = normalized.rotate(v);
            assertEquals(r1.x(), r2.x(), EPSILON);
            assertEquals(r1.y(), r2.y(), EPSILON);
            assertEquals(r1.z(), r2.z(), EPSILON);
        }
        @Test void conjugateInvertsRotation() {
            var q = Quat.fromAxisAngle(Vec3.UNIT_Y, (float) Math.toRadians(45));
            var v = new Vec3(1f, 0f, 0f);
            var rotated = q.rotate(v);
            var back = q.conjugate().rotate(rotated);
            assertEquals(v.x(), back.x(), EPSILON);
            assertEquals(v.y(), back.y(), EPSILON);
            assertEquals(v.z(), back.z(), EPSILON);
        }
        @Test void toMat4MatchesDirectRotation() {
            var q = Quat.fromAxisAngle(Vec3.UNIT_Y, (float) Math.toRadians(45));
            var mat = q.toMat4();
            var v = new Vec4(1f, 0f, 0f, 1f);
            var fromQuat = q.rotate(v.xyz());
            var fromMat = mat.transform(v);
            assertEquals(fromQuat.x(), fromMat.x(), EPSILON);
            assertEquals(fromQuat.y(), fromMat.y(), EPSILON);
            assertEquals(fromQuat.z(), fromMat.z(), EPSILON);
        }
        @Test void slerp() {
            var a = Quat.IDENTITY;
            var b = Quat.fromAxisAngle(Vec3.UNIT_Y, (float) Math.toRadians(90));
            var mid = a.slerp(b, 0.5f);
            var result = mid.rotate(new Vec3(1f, 0f, 0f));
            // 45 degrees around Y: x ~ cos(45), z ~ -sin(45)
            assertEquals((float) Math.cos(Math.toRadians(45)), result.x(), EPSILON);
            assertEquals(0f, result.y(), EPSILON);
            assertEquals(-(float) Math.sin(Math.toRadians(45)), result.z(), EPSILON);
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement Quat**

```java
package dev.engine.core.math;

public record Quat(float x, float y, float z, float w) {

    public static final Quat IDENTITY = new Quat(0f, 0f, 0f, 1f);

    public static Quat fromAxisAngle(Vec3 axis, float radians) {
        float half = radians * 0.5f;
        float s = (float) Math.sin(half);
        Vec3 n = axis.normalize();
        return new Quat(n.x() * s, n.y() * s, n.z() * s, (float) Math.cos(half));
    }

    public Quat mul(Quat o) {
        return new Quat(
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w,
                w * o.w - x * o.x - y * o.y - z * o.z
        );
    }

    public Quat conjugate() { return new Quat(-x, -y, -z, w); }

    public float lengthSquared() { return x * x + y * y + z * z + w * w; }
    public float length() { return (float) Math.sqrt(lengthSquared()); }

    public Quat normalize() {
        float len = length();
        return new Quat(x / len, y / len, z / len, w / len);
    }

    public Vec3 rotate(Vec3 v) {
        Quat p = new Quat(v.x(), v.y(), v.z(), 0f);
        Quat result = this.mul(p).mul(this.conjugate());
        return new Vec3(result.x(), result.y(), result.z());
    }

    public Quat slerp(Quat other, float t) {
        float dot = x * other.x + y * other.y + z * other.z + w * other.w;
        Quat to = other;
        if (dot < 0f) {
            dot = -dot;
            to = new Quat(-other.x, -other.y, -other.z, -other.w);
        }
        if (dot > 0.9995f) {
            // Linear interpolation for very close quaternions
            return new Quat(
                    x + (to.x - x) * t, y + (to.y - y) * t,
                    z + (to.z - z) * t, w + (to.w - w) * t
            ).normalize();
        }
        float theta = (float) Math.acos(dot);
        float sinTheta = (float) Math.sin(theta);
        float a = (float) Math.sin((1 - t) * theta) / sinTheta;
        float b = (float) Math.sin(t * theta) / sinTheta;
        return new Quat(
                x * a + to.x * b, y * a + to.y * b,
                z * a + to.z * b, w * a + to.w * b
        );
    }

    public Mat4 toMat4() {
        float xx = x * x, yy = y * y, zz = z * z;
        float xy = x * y, xz = x * z, yz = y * z;
        float wx = w * x, wy = w * y, wz = w * z;
        return new Mat4(
                1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy), 0,
                2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx), 0,
                2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy), 0,
                0, 0, 0, 1
        );
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

---

## Task 7: Handle System

**Files:**
- Create: `core/src/main/java/dev/engine/core/handle/Handle.java`
- Create: `core/src/main/java/dev/engine/core/handle/HandlePool.java`
- Create: `core/src/test/java/dev/engine/core/handle/HandlePoolTest.java`

- [ ] **Step 1: Write failing tests**

```java
package dev.engine.core.handle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;

class HandlePoolTest {

    private HandlePool pool;

    @BeforeEach
    void setUp() { pool = new HandlePool(); }

    @Nested
    class Allocation {
        @Test void allocateReturnsValidHandle() {
            var h = pool.allocate();
            assertTrue(pool.isValid(h));
        }
        @Test void eachAllocationIsUnique() {
            var a = pool.allocate();
            var b = pool.allocate();
            assertNotEquals(a, b);
        }
    }

    @Nested
    class Release {
        @Test void releasedHandleIsInvalid() {
            var h = pool.allocate();
            pool.release(h);
            assertFalse(pool.isValid(h));
        }
        @Test void doubleReleaseIsHarmless() {
            var h = pool.allocate();
            pool.release(h);
            assertDoesNotThrow(() -> pool.release(h));
        }
        @Test void reusedSlotHasDifferentGeneration() {
            var h1 = pool.allocate();
            int index = h1.index();
            pool.release(h1);
            var h2 = pool.allocate();
            assertEquals(index, h2.index());
            assertNotEquals(h1.generation(), h2.generation());
            assertFalse(pool.isValid(h1));
            assertTrue(pool.isValid(h2));
        }
    }

    @Nested
    class ThreadSafety {
        @Test void concurrentAllocateAndRelease() throws InterruptedException {
            int threads = 8;
            int opsPerThread = 1000;
            var allHandles = new ConcurrentLinkedQueue<Handle>();
            var latch = new CountDownLatch(threads);

            try (var executor = Executors.newFixedThreadPool(threads)) {
                for (int t = 0; t < threads; t++) {
                    executor.submit(() -> {
                        for (int i = 0; i < opsPerThread; i++) {
                            var h = pool.allocate();
                            allHandles.add(h);
                            if (i % 3 == 0) pool.release(h);
                        }
                        latch.countDown();
                    });
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS));
            }
            // No exceptions, no corruption — just verify pool is usable
            var h = pool.allocate();
            assertTrue(pool.isValid(h));
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement Handle and HandlePool**

```java
// Handle.java
package dev.engine.core.handle;

public record Handle(int index, int generation) {
    public static final Handle INVALID = new Handle(-1, 0);
}
```

```java
// HandlePool.java
package dev.engine.core.handle;

import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Deque;

public class HandlePool {

    private final List<Integer> generations = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private final Object lock = new Object();

    public Handle allocate() {
        synchronized (lock) {
            if (!freeIndices.isEmpty()) {
                int index = freeIndices.poll();
                int gen = generations.get(index);
                return new Handle(index, gen);
            }
            int index = generations.size();
            generations.add(0);
            return new Handle(index, 0);
        }
    }

    public void release(Handle handle) {
        synchronized (lock) {
            if (handle.index() < 0 || handle.index() >= generations.size()) return;
            if (generations.get(handle.index()) != handle.generation()) return;
            generations.set(handle.index(), handle.generation() + 1);
            freeIndices.add(handle.index());
        }
    }

    public boolean isValid(Handle handle) {
        synchronized (lock) {
            if (handle.index() < 0 || handle.index() >= generations.size()) return false;
            return generations.get(handle.index()) == handle.generation();
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

---

## Task 8: Native Resource Tracking

**Files:**
- Create: `core/src/main/java/dev/engine/core/resource/ResourceCleaner.java`
- Create: `core/src/main/java/dev/engine/core/resource/NativeResource.java`
- Create: `core/src/test/java/dev/engine/core/resource/NativeResourceTest.java`

- [ ] **Step 1: Write failing tests**

```java
package dev.engine.core.resource;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeResourceTest {

    static class TestResource extends NativeResource {
        final AtomicBoolean freed;
        TestResource(AtomicBoolean freed) {
            super(freed::set);
            this.freed = freed;
        }
        private TestResource(Runnable cleanupAction, AtomicBoolean freed) {
            super(cleanupAction);
            this.freed = freed;
        }
    }

    @Nested
    class DeterministicCleanup {
        @Test void closeFreesResource() {
            var freed = new AtomicBoolean(false);
            var resource = new TestResource(freed);
            resource.close();
            assertTrue(freed.get());
        }

        @Test void doubleCloseIsHarmless() {
            var freed = new AtomicBoolean(false);
            var resource = new TestResource(freed);
            resource.close();
            assertDoesNotThrow(resource::close);
        }

        @Test void isClosedReportsCorrectly() {
            var resource = new TestResource(new AtomicBoolean());
            assertFalse(resource.isClosed());
            resource.close();
            assertTrue(resource.isClosed());
        }
    }

    @Nested
    class CleanerSafetyNet {
        @Test void cleanerFreesWhenNotExplicitlyClosed() throws InterruptedException {
            var freed = new AtomicBoolean(false);
            allocateAndAbandon(freed);
            // Force GC to trigger Cleaner
            for (int i = 0; i < 10 && !freed.get(); i++) {
                System.gc();
                Thread.sleep(50);
            }
            assertTrue(freed.get(), "Cleaner should have freed the resource");
        }

        private void allocateAndAbandon(AtomicBoolean freed) {
            new TestResource(freed);
            // Resource goes out of scope without close()
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement ResourceCleaner and NativeResource**

```java
// ResourceCleaner.java
package dev.engine.core.resource;

import java.lang.ref.Cleaner;

public final class ResourceCleaner {

    private static final Cleaner CLEANER = Cleaner.create();

    private ResourceCleaner() {}

    static Cleaner.Cleanable register(Object resource, Runnable cleanupAction) {
        return CLEANER.register(resource, cleanupAction);
    }
}
```

```java
// NativeResource.java
package dev.engine.core.resource;

import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class NativeResource implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final Cleaner.Cleanable cleanable;
    private final CleanupAction cleanupAction;

    protected NativeResource(Runnable releaseAction) {
        this.cleanupAction = new CleanupAction(releaseAction);
        this.cleanable = ResourceCleaner.register(this, cleanupAction);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            cleanable.clean();
        }
    }

    public boolean isClosed() {
        return closed.get();
    }

    private static class CleanupAction implements Runnable {
        private final Runnable release;
        private final AtomicBoolean executed = new AtomicBoolean(false);

        CleanupAction(Runnable release) { this.release = release; }

        @Override
        public void run() {
            if (executed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

---

## Task 9: Property Map

**Files:**
- Create: `core/src/main/java/dev/engine/core/property/PropertyKey.java`
- Create: `core/src/main/java/dev/engine/core/property/PropertyMap.java`
- Create: `core/src/main/java/dev/engine/core/property/MutablePropertyMap.java`
- Create: `core/src/test/java/dev/engine/core/property/PropertyMapTest.java`

- [ ] **Step 1: Write failing tests**

```java
package dev.engine.core.property;

import dev.engine.core.math.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PropertyMapTest {

    static final PropertyKey<Float> ROUGHNESS = PropertyKey.of("roughness", Float.class);
    static final PropertyKey<Vec3> ALBEDO = PropertyKey.of("albedo", Vec3.class);
    static final PropertyKey<Boolean> TRANSPARENT = PropertyKey.of("transparent", Boolean.class);

    @Nested
    class ImmutableMap {
        @Test void getReturnsSetValue() {
            var map = PropertyMap.builder()
                    .set(ROUGHNESS, 0.5f)
                    .set(ALBEDO, new Vec3(1f, 0f, 0f))
                    .build();
            assertEquals(0.5f, map.get(ROUGHNESS));
            assertEquals(new Vec3(1f, 0f, 0f), map.get(ALBEDO));
        }

        @Test void getMissingReturnsNull() {
            var map = PropertyMap.builder().build();
            assertNull(map.get(ROUGHNESS));
        }

        @Test void containsKey() {
            var map = PropertyMap.builder().set(ROUGHNESS, 0.5f).build();
            assertTrue(map.contains(ROUGHNESS));
            assertFalse(map.contains(ALBEDO));
        }

        @Test void keys() {
            var map = PropertyMap.builder()
                    .set(ROUGHNESS, 0.5f)
                    .set(ALBEDO, Vec3.ONE)
                    .build();
            var keys = map.keys();
            assertEquals(2, keys.size());
            assertTrue(keys.contains(ROUGHNESS));
            assertTrue(keys.contains(ALBEDO));
        }

        @Test void equalityByContent() {
            var a = PropertyMap.builder().set(ROUGHNESS, 0.5f).build();
            var b = PropertyMap.builder().set(ROUGHNESS, 0.5f).build();
            assertEquals(a, b);
        }
    }

    @Nested
    class MutableMapTests {
        @Test void setAndGet() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            assertEquals(0.5f, map.get(ROUGHNESS));
        }

        @Test void trackChanges() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            map.clearChanges();
            map.set(ROUGHNESS, 0.8f);
            var changes = map.getChanges();
            assertEquals(1, changes.size());
            assertTrue(changes.contains(ROUGHNESS));
        }

        @Test void noChangeIfSameValue() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            map.clearChanges();
            map.set(ROUGHNESS, 0.5f);
            assertTrue(map.getChanges().isEmpty());
        }

        @Test void snapshot() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            map.set(ALBEDO, Vec3.ONE);
            var snapshot = map.snapshot();
            assertEquals(0.5f, snapshot.get(ROUGHNESS));
            assertEquals(Vec3.ONE, snapshot.get(ALBEDO));
        }

        @Test void removeTracksChange() {
            var map = new MutablePropertyMap();
            map.set(ROUGHNESS, 0.5f);
            map.clearChanges();
            map.remove(ROUGHNESS);
            assertNull(map.get(ROUGHNESS));
            assertTrue(map.getChanges().contains(ROUGHNESS));
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement PropertyKey, PropertyMap, MutablePropertyMap**

```java
// PropertyKey.java
package dev.engine.core.property;

import java.util.Objects;

public final class PropertyKey<T> {

    private final String name;
    private final Class<T> type;

    private PropertyKey(String name, Class<T> type) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public static <T> PropertyKey<T> of(String name, Class<T> type) {
        return new PropertyKey<>(name, type);
    }

    public String name() { return name; }
    public Class<T> type() { return type; }

    @Override public boolean equals(Object o) {
        return this == o || (o instanceof PropertyKey<?> k && name.equals(k.name) && type.equals(k.type));
    }
    @Override public int hashCode() { return Objects.hash(name, type); }
    @Override public String toString() { return "PropertyKey[" + name + ":" + type.getSimpleName() + "]"; }
}
```

```java
// PropertyMap.java
package dev.engine.core.property;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class PropertyMap {

    private final Map<PropertyKey<?>, Object> values;

    private PropertyMap(Map<PropertyKey<?>, Object> values) {
        this.values = Map.copyOf(values);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(PropertyKey<T> key) {
        return (T) values.get(key);
    }

    public boolean contains(PropertyKey<?> key) {
        return values.containsKey(key);
    }

    public Set<PropertyKey<?>> keys() {
        return values.keySet();
    }

    public int size() { return values.size(); }

    public static Builder builder() { return new Builder(); }

    @Override public boolean equals(Object o) {
        return this == o || (o instanceof PropertyMap p && values.equals(p.values));
    }
    @Override public int hashCode() { return values.hashCode(); }

    public static class Builder {
        private final Map<PropertyKey<?>, Object> values = new LinkedHashMap<>();

        public <T> Builder set(PropertyKey<T> key, T value) {
            values.put(key, value);
            return this;
        }

        public PropertyMap build() { return new PropertyMap(values); }
    }
}
```

```java
// MutablePropertyMap.java
package dev.engine.core.property;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MutablePropertyMap {

    private final Map<PropertyKey<?>, Object> values = new LinkedHashMap<>();
    private final Set<PropertyKey<?>> changes = new LinkedHashSet<>();

    @SuppressWarnings("unchecked")
    public <T> T get(PropertyKey<T> key) {
        return (T) values.get(key);
    }

    public <T> void set(PropertyKey<T> key, T value) {
        Object old = values.put(key, value);
        if (!Objects.equals(old, value)) {
            changes.add(key);
        }
    }

    public void remove(PropertyKey<?> key) {
        if (values.remove(key) != null) {
            changes.add(key);
        }
    }

    public boolean contains(PropertyKey<?> key) {
        return values.containsKey(key);
    }

    public Set<PropertyKey<?>> getChanges() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(changes));
    }

    public void clearChanges() { changes.clear(); }

    public PropertyMap snapshot() {
        var builder = PropertyMap.builder();
        for (var entry : values.entrySet()) {
            @SuppressWarnings("unchecked")
            var key = (PropertyKey<Object>) entry.getKey();
            builder.set(key, entry.getValue());
        }
        return builder.build();
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**
- [ ] **Step 5: Commit**

---

## Task 10: Run all tests, final commit

- [ ] **Step 1: Run full test suite**

Run: `./gradlew :core:test --console=plain`
Expected: ALL PASS

- [ ] **Step 2: Final commit for Phase 1**

```bash
git add -A
git commit -m "feat(core): complete Phase 1 foundation — event bus, math, handles, resources, properties"
```
