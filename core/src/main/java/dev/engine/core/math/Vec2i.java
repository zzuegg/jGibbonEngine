package dev.engine.core.math;

public record Vec2i(int x, int y) {

    public static final Vec2i ZERO = new Vec2i(0, 0);
    public static final Vec2i ONE = new Vec2i(1, 1);

    public Vec2i add(Vec2i o) { return new Vec2i(x + o.x, y + o.y); }
    public Vec2i sub(Vec2i o) { return new Vec2i(x - o.x, y - o.y); }
    public Vec2i mul(Vec2i o) { return new Vec2i(x * o.x, y * o.y); }
    public Vec2i scale(int s) { return new Vec2i(x * s, y * s); }
    public Vec2i negate() { return new Vec2i(-x, -y); }
}
