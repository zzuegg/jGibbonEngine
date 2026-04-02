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
            var m = t.mul(s);
            var result = m.transform(new Vec4(1f, 0f, 0f, 1f));
            assertEquals(3f, result.x(), EPSILON);
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
            var result = m.transform(new Vec4(0f, 0f, -5f, 1f));
            assertEquals(0f, result.x(), EPSILON);
            assertEquals(0f, result.y(), EPSILON);
        }
    }
}
