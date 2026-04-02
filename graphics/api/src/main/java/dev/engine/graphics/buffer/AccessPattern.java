package dev.engine.graphics.buffer;

public interface AccessPattern {
    String name();

    AccessPattern STATIC = NamedAccess.of("STATIC");
    AccessPattern DYNAMIC = NamedAccess.of("DYNAMIC");
    AccessPattern STREAM = NamedAccess.of("STREAM");
}

record NamedAccess(String name) implements AccessPattern {
    static AccessPattern of(String name) { return new NamedAccess(name); }
}
