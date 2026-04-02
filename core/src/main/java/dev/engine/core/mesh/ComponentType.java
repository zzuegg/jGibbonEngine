package dev.engine.core.mesh;

public interface ComponentType {
    String name();
    int sizeInBytes();

    ComponentType FLOAT = new ComponentType() {
        @Override public String name() { return "FLOAT"; }
        @Override public int sizeInBytes() { return 4; }
    };

    ComponentType BYTE = new ComponentType() {
        @Override public String name() { return "BYTE"; }
        @Override public int sizeInBytes() { return 1; }
    };

    ComponentType INT = new ComponentType() {
        @Override public String name() { return "INT"; }
        @Override public int sizeInBytes() { return 4; }
    };
}
