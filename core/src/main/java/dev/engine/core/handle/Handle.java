package dev.engine.core.handle;

/**
 * Generational opaque handle with a phantom type parameter for compile-time safety.
 *
 * @param <T> phantom type tag — prevents mixing handles of different resource types
 */
public record Handle<T>(int index, int generation) {

    @SuppressWarnings("unchecked")
    public static <T> Handle<T> invalid() {
        return (Handle<T>) INVALID_RAW;
    }

    private static final Handle<?> INVALID_RAW = new Handle<>(-1, 0);
}
