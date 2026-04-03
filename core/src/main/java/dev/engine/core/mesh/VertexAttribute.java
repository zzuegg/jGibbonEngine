package dev.engine.core.mesh;

public record VertexAttribute(int location, int componentCount, ComponentType componentType, boolean normalized, int offset, int divisor) {

    // Backward-compatible constructor (divisor = 0, per-vertex)
    public VertexAttribute(int location, int componentCount, ComponentType componentType, boolean normalized, int offset) {
        this(location, componentCount, componentType, normalized, offset, 0);
    }

    public int sizeInBytes() {
        return componentCount * componentType.sizeInBytes();
    }

    public boolean isPerInstance() {
        return divisor > 0;
    }
}
