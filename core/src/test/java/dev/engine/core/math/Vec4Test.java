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
        @Test void dot() { assertEquals(60f, new Vec4(1f, 2f, 3f, 4f).dot(new Vec4(4f, 5f, 6f, 7f)), EPSILON); }
        @Test void length() { assertEquals(2f, new Vec4(1f, 1f, 1f, 1f).length(), EPSILON); }
        @Test void normalize() { assertEquals(1f, new Vec4(1f, 2f, 3f, 4f).normalize().length(), EPSILON); }
        @Test void xyz() { assertEquals(new Vec3(1f, 2f, 3f), new Vec4(1f, 2f, 3f, 4f).xyz()); }
    }
}
