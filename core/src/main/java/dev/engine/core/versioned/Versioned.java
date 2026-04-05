package dev.engine.core.versioned;

/**
 * Thread-safe versioned container. Each {@link #set} call increments the version.
 * Create {@link Reference} instances to track whether the value has changed since
 * last checked.
 *
 * <pre>{@code
 * var size = new Versioned<>(new Vec2i(800, 600));
 * var ref = size.createReference();
 *
 * // Provider updates
 * size.set(new Vec2i(1920, 1080));
 *
 * // Consumer checks
 * if (ref.update()) {
 *     resize(ref.getValue());
 * }
 * }</pre>
 */
public class Versioned<T> {

    private volatile long version;
    private volatile T value;

    public Versioned(T value) {
        this.value = value;
        this.version = value != null ? 1 : 0;
    }

    public synchronized void set(T value) {
        version++;
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    long getVersion() {
        return version;
    }

    public Reference<T> createReference() {
        return new Reference<>(this);
    }
}
