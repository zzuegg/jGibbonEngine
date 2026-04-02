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
