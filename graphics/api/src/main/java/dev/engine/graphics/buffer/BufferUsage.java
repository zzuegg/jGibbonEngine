package dev.engine.graphics.buffer;

public interface BufferUsage {
    String name();

    BufferUsage VERTEX = NamedUsage.of("VERTEX");
    BufferUsage INDEX = NamedUsage.of("INDEX");
    BufferUsage UNIFORM = NamedUsage.of("UNIFORM");
    BufferUsage STORAGE = NamedUsage.of("STORAGE");
}

record NamedUsage(String name) implements BufferUsage {
    static BufferUsage of(String name) { return new NamedUsage(name); }
}
