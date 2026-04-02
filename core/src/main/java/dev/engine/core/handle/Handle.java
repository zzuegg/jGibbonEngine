package dev.engine.core.handle;

public record Handle(int index, int generation) {

    public static final Handle INVALID = new Handle(-1, 0);
}
