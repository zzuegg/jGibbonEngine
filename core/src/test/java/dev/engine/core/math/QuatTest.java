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
            assertEquals((float) Math.cos(Math.toRadians(45)), result.x(), EPSILON);
            assertEquals(0f, result.y(), EPSILON);
            assertEquals(-(float) Math.sin(Math.toRadians(45)), result.z(), EPSILON);
        }
    }
}
