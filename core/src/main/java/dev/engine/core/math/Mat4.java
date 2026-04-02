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
