package dev.engine.core.math;

public record Mat3(
        float m00, float m01, float m02,
        float m10, float m11, float m12,
        float m20, float m21, float m22
) {

    public static final Mat3 IDENTITY = new Mat3(
            1, 0, 0,
            0, 1, 0,
            0, 0, 1
    );

    public Mat3 mul(Mat3 o) {
        return new Mat3(
                m00*o.m00 + m01*o.m10 + m02*o.m20,
                m00*o.m01 + m01*o.m11 + m02*o.m21,
                m00*o.m02 + m01*o.m12 + m02*o.m22,

                m10*o.m00 + m11*o.m10 + m12*o.m20,
                m10*o.m01 + m11*o.m11 + m12*o.m21,
                m10*o.m02 + m11*o.m12 + m12*o.m22,

                m20*o.m00 + m21*o.m10 + m22*o.m20,
                m20*o.m01 + m21*o.m11 + m22*o.m21,
                m20*o.m02 + m21*o.m12 + m22*o.m22
        );
    }

    public Vec3 transform(Vec3 v) {
        return new Vec3(
                m00*v.x() + m01*v.y() + m02*v.z(),
                m10*v.x() + m11*v.y() + m12*v.z(),
                m20*v.x() + m21*v.y() + m22*v.z()
        );
    }

    public Mat3 transpose() {
        return new Mat3(
                m00, m10, m20,
                m01, m11, m21,
                m02, m12, m22
        );
    }

    public float determinant() {
        return m00 * (m11 * m22 - m12 * m21)
             - m01 * (m10 * m22 - m12 * m20)
             + m02 * (m10 * m21 - m11 * m20);
    }

    public Mat3 inverse() {
        float det = determinant();
        float invDet = 1f / det;
        return new Mat3(
                 (m11 * m22 - m12 * m21) * invDet,
                -(m01 * m22 - m02 * m21) * invDet,
                 (m01 * m12 - m02 * m11) * invDet,
                -(m10 * m22 - m12 * m20) * invDet,
                 (m00 * m22 - m02 * m20) * invDet,
                -(m00 * m12 - m02 * m10) * invDet,
                 (m10 * m21 - m11 * m20) * invDet,
                -(m00 * m21 - m01 * m20) * invDet,
                 (m00 * m11 - m01 * m10) * invDet
        );
    }

    /** Computes the normal matrix (inverse transpose) from this 3x3 matrix. */
    public Mat3 normalMatrix() {
        return inverse().transpose();
    }

    public Mat3 scale(float s) {
        return new Mat3(
                m00*s, m01*s, m02*s,
                m10*s, m11*s, m12*s,
                m20*s, m21*s, m22*s
        );
    }
}
