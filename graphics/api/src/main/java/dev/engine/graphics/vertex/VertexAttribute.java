package dev.engine.graphics.vertex;

public record VertexAttribute(int location, int componentCount, ComponentType componentType, boolean normalized, int offset) {

    public int sizeInBytes() {
        return componentCount * componentType.sizeInBytes();
    }
}
