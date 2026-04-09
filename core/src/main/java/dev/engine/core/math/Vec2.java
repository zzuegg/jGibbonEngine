package dev.engine.core.math;

public record Vec2(float x, float y) {

    public static final Vec2 ZERO = new Vec2(0f, 0f);
    public static final Vec2 ONE = new Vec2(1f, 1f);
    public static final Vec2 UNIT_X = new Vec2(1f, 0f);
    public static final Vec2 UNIT_Y = new Vec2(0f, 1f);

    public Vec2 add(Vec2 o) { return new Vec2(x + o.x, y + o.y); }
    public Vec2 sub(Vec2 o) { return new Vec2(x - o.x, y - o.y); }
    public Vec2 mul(Vec2 o) { return new Vec2(x * o.x, y * o.y); }
    public Vec2 scale(float s) { return new Vec2(x * s, y * s); }
    public Vec2 negate() { return new Vec2(-x, -y); }
    public float dot(Vec2 o) { return x * o.x + y * o.y; }
    public float lengthSquared() { return x * x + y * y; }
    public float length() { return (float) Math.sqrt(lengthSquared()); }
    public Vec2 normalize() { float len = length(); return len == 0f ? ZERO : new Vec2(x / len, y / len); }
    public Vec2 lerp(Vec2 o, float t) { return new Vec2(x + (o.x - x) * t, y + (o.y - y) * t); }
}
