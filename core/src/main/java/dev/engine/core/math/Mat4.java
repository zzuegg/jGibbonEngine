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

    public static Mat4 ortho(float left, float right, float bottom, float top, float near, float far) {
        return new Mat4(
                2f / (right - left), 0, 0, -(right + left) / (right - left),
                0, 2f / (top - bottom), 0, -(top + bottom) / (top - bottom),
                0, 0, -2f / (far - near), -(far + near) / (far - near),
                0, 0, 0, 1
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

    public float determinant() {
        float a = m00 * (m11 * (m22 * m33 - m23 * m32) - m12 * (m21 * m33 - m23 * m31) + m13 * (m21 * m32 - m22 * m31));
        float b = m01 * (m10 * (m22 * m33 - m23 * m32) - m12 * (m20 * m33 - m23 * m30) + m13 * (m20 * m32 - m22 * m30));
        float c = m02 * (m10 * (m21 * m33 - m23 * m31) - m11 * (m20 * m33 - m23 * m30) + m13 * (m20 * m31 - m21 * m30));
        float d = m03 * (m10 * (m21 * m32 - m22 * m31) - m11 * (m20 * m32 - m22 * m30) + m12 * (m20 * m31 - m21 * m30));
        return a - b + c - d;
    }

    public Mat4 inverse() {
        float a2323 = m22 * m33 - m23 * m32;
        float a1323 = m21 * m33 - m23 * m31;
        float a1223 = m21 * m32 - m22 * m31;
        float a0323 = m20 * m33 - m23 * m30;
        float a0223 = m20 * m32 - m22 * m30;
        float a0123 = m20 * m31 - m21 * m30;
        float a2313 = m12 * m33 - m13 * m32;
        float a1313 = m11 * m33 - m13 * m31;
        float a1213 = m11 * m32 - m12 * m31;
        float a2312 = m12 * m23 - m13 * m22;
        float a1312 = m11 * m23 - m13 * m21;
        float a1212 = m11 * m22 - m12 * m21;
        float a0313 = m10 * m33 - m13 * m30;
        float a0213 = m10 * m32 - m12 * m30;
        float a0312 = m10 * m23 - m13 * m20;
        float a0212 = m10 * m22 - m12 * m20;
        float a0113 = m10 * m31 - m11 * m30;
        float a0112 = m10 * m21 - m11 * m20;

        float det = m00 * (m11 * a2323 - m12 * a1323 + m13 * a1223)
                  - m01 * (m10 * a2323 - m12 * a0323 + m13 * a0223)
                  + m02 * (m10 * a1323 - m11 * a0323 + m13 * a0123)
                  - m03 * (m10 * a1223 - m11 * a0223 + m12 * a0123);

        float invDet = 1f / det;

        return new Mat4(
                 (m11 * a2323 - m12 * a1323 + m13 * a1223) * invDet,
                -(m01 * a2323 - m02 * a1323 + m03 * a1223) * invDet,
                 (m01 * a2313 - m02 * a1313 + m03 * a1213) * invDet,
                -(m01 * a2312 - m02 * a1312 + m03 * a1212) * invDet,
                -(m10 * a2323 - m12 * a0323 + m13 * a0223) * invDet,
                 (m00 * a2323 - m02 * a0323 + m03 * a0223) * invDet,
                -(m00 * a2313 - m02 * a0313 + m03 * a0213) * invDet,
                 (m00 * a2312 - m02 * a0312 + m03 * a0212) * invDet,
                 (m10 * a1323 - m11 * a0323 + m13 * a0123) * invDet,
                -(m00 * a1323 - m01 * a0323 + m03 * a0123) * invDet,
                 (m00 * a1313 - m01 * a0313 + m03 * a0113) * invDet,
                -(m00 * a1312 - m01 * a0312 + m03 * a0112) * invDet,
                -(m10 * a1223 - m11 * a0223 + m12 * a0123) * invDet,
                 (m00 * a1223 - m01 * a0223 + m02 * a0123) * invDet,
                -(m00 * a1213 - m01 * a0213 + m02 * a0113) * invDet,
                 (m00 * a1212 - m01 * a0212 + m02 * a0112) * invDet
        );
    }

    /** Extracts the upper-left 3x3 submatrix. */
    public Mat3 toMat3() {
        return new Mat3(
                m00, m01, m02,
                m10, m11, m12,
                m20, m21, m22
        );
    }

    /**
     * Writes this matrix to a {@link dev.engine.core.memory.NativeMemory} in column-major order (GPU layout).
     * GPU APIs (OpenGL, Vulkan) expect columns stored contiguously.
     */
    public void writeGpu(dev.engine.core.memory.NativeMemory mem, long offset) {
        mem.putFloat(offset,      m00);
        mem.putFloat(offset + 4,  m10);
        mem.putFloat(offset + 8,  m20);
        mem.putFloat(offset + 12, m30);
        mem.putFloat(offset + 16, m01);
        mem.putFloat(offset + 20, m11);
        mem.putFloat(offset + 24, m21);
        mem.putFloat(offset + 28, m31);
        mem.putFloat(offset + 32, m02);
        mem.putFloat(offset + 36, m12);
        mem.putFloat(offset + 40, m22);
        mem.putFloat(offset + 44, m32);
        mem.putFloat(offset + 48, m03);
        mem.putFloat(offset + 52, m13);
        mem.putFloat(offset + 56, m23);
        mem.putFloat(offset + 60, m33);
    }
}
