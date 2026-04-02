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
