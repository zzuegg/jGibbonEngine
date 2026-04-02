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
        @Test void zero() { assertEquals(new Vec2(0f, 0f), Vec2.ZERO); }
        @Test void one() { assertEquals(new Vec2(1f, 1f), Vec2.ONE); }
        @Test void unitX() { assertEquals(new Vec2(1f, 0f), Vec2.UNIT_X); }
        @Test void unitY() { assertEquals(new Vec2(0f, 1f), Vec2.UNIT_Y); }
    }

    @Nested
    class Arithmetic {
        @Test void add() { assertEquals(new Vec2(3f, 5f), new Vec2(1f, 2f).add(new Vec2(2f, 3f))); }
        @Test void sub() { assertEquals(new Vec2(-1f, -1f), new Vec2(1f, 2f).sub(new Vec2(2f, 3f))); }
        @Test void mul() { assertEquals(new Vec2(2f, 6f), new Vec2(1f, 2f).mul(new Vec2(2f, 3f))); }
        @Test void scale() { assertEquals(new Vec2(3f, 6f), new Vec2(1f, 2f).scale(3f)); }
        @Test void negate() { assertEquals(new Vec2(-1f, -2f), new Vec2(1f, 2f).negate()); }
    }

    @Nested
    class VectorOps {
        @Test void dot() { assertEquals(11f, new Vec2(1f, 2f).dot(new Vec2(3f, 4f)), EPSILON); }
        @Test void length() { assertEquals(5f, new Vec2(3f, 4f).length(), EPSILON); }
        @Test void lengthSquared() { assertEquals(25f, new Vec2(3f, 4f).lengthSquared(), EPSILON); }
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
