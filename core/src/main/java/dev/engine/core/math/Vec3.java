package dev.engine.core.math;

public record Vec3(float x, float y, float z) {

    public static final Vec3 ZERO = new Vec3(0f, 0f, 0f);
    public static final Vec3 ONE = new Vec3(1f, 1f, 1f);
    public static final Vec3 UNIT_X = new Vec3(1f, 0f, 0f);
    public static final Vec3 UNIT_Y = new Vec3(0f, 1f, 0f);
    public static final Vec3 UNIT_Z = new Vec3(0f, 0f, 1f);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 mul(Vec3 o) { return new Vec3(x * o.x, y * o.y, z * o.z); }
    public Vec3 scale(float s) { return new Vec3(x * s, y * s, z * s); }
    public Vec3 negate() { return new Vec3(-x, -y, -z); }
    public float dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
    public Vec3 cross(Vec3 o) { return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public float lengthSquared() { return x * x + y * y + z * z; }
    public float length() { return (float) Math.sqrt(lengthSquared()); }
    public Vec3 normalize() { float len = length(); return len == 0f ? ZERO : new Vec3(x / len, y / len, z / len); }
    public Vec3 lerp(Vec3 o, float t) { return new Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t); }

    public float distance(Vec3 o) { return sub(o).length(); }
    public float distanceSquared(Vec3 o) { return sub(o).lengthSquared(); }

    public Vec3 reflect(Vec3 normal) {
        float d = 2f * dot(normal);
        return new Vec3(x - d * normal.x, y - d * normal.y, z - d * normal.z);
    }

    public Vec3 abs() { return new Vec3(Math.abs(x), Math.abs(y), Math.abs(z)); }

    public Vec3 min(Vec3 o) { return new Vec3(Math.min(x, o.x), Math.min(y, o.y), Math.min(z, o.z)); }
    public Vec3 max(Vec3 o) { return new Vec3(Math.max(x, o.x), Math.max(y, o.y), Math.max(z, o.z)); }

    public Vec3 clamp(Vec3 min, Vec3 max) {
        return new Vec3(
                Math.max(min.x, Math.min(max.x, x)),
                Math.max(min.y, Math.min(max.y, y)),
                Math.max(min.z, Math.min(max.z, z))
        );
    }

    public Vec3 clamp(float min, float max) {
        return new Vec3(
                Math.max(min, Math.min(max, x)),
                Math.max(min, Math.min(max, y)),
                Math.max(min, Math.min(max, z))
        );
    }
}
