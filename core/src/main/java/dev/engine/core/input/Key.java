package dev.engine.core.input;

public interface Key {
    String name();
    static Key of(String name) { return new NamedKey(name); }
}

record NamedKey(String name) implements Key {}
