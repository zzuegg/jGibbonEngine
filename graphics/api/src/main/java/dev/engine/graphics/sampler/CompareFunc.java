package dev.engine.graphics.sampler;

/**
 * Comparison function for depth/shadow samplers.
 * Used when the sampler is configured for comparison mode (e.g., shadow maps).
 */
public interface CompareFunc {
    String name();

    CompareFunc NEVER = NamedCompare.of("NEVER");
    CompareFunc LESS = NamedCompare.of("LESS");
    CompareFunc EQUAL = NamedCompare.of("EQUAL");
    CompareFunc LESS_EQUAL = NamedCompare.of("LESS_EQUAL");
    CompareFunc GREATER = NamedCompare.of("GREATER");
    CompareFunc NOT_EQUAL = NamedCompare.of("NOT_EQUAL");
    CompareFunc GREATER_EQUAL = NamedCompare.of("GREATER_EQUAL");
    CompareFunc ALWAYS = NamedCompare.of("ALWAYS");
}

record NamedCompare(String name) implements CompareFunc {
    static CompareFunc of(String name) { return new NamedCompare(name); }
}
