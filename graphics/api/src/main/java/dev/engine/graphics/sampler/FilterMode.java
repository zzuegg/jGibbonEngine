package dev.engine.graphics.sampler;

public interface FilterMode {
    String name();

    FilterMode NEAREST = NamedFilter.of("NEAREST");
    FilterMode LINEAR = NamedFilter.of("LINEAR");
    FilterMode NEAREST_MIPMAP_NEAREST = NamedFilter.of("NEAREST_MIPMAP_NEAREST");
    FilterMode NEAREST_MIPMAP_LINEAR = NamedFilter.of("NEAREST_MIPMAP_LINEAR");
    FilterMode LINEAR_MIPMAP_NEAREST = NamedFilter.of("LINEAR_MIPMAP_NEAREST");
    FilterMode LINEAR_MIPMAP_LINEAR = NamedFilter.of("LINEAR_MIPMAP_LINEAR");
}

record NamedFilter(String name) implements FilterMode {
    static FilterMode of(String name) { return new NamedFilter(name); }
}
