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
