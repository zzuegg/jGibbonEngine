package dev.engine.core.versioned;

/**
 * Tracks staleness relative to a {@link Versioned} container.
 * Call {@link #update()} to check if the value changed since the last call.
 * Multiple references to the same Versioned track independently.
 */
public class Reference<T> {

    private long version = 0;
    private final Versioned<T> versioned;
    private T value;

    Reference(Versioned<T> versioned) {
        this.versioned = versioned;
        if (versioned.getValue() != null) {
            version = -1;
        }
    }

    /**
     * Returns true if the value changed since the last call to update().
     * Caches the current value internally.
     */
    public boolean update() {
        if (version != versioned.getVersion()) {
            version = versioned.getVersion();
            value = versioned.getValue();
            return true;
        }
        return false;
    }

    /** Returns the cached value from the last {@link #update()} call. */
    public T getValue() {
        return value;
    }

    /** Writes a value back through to the underlying Versioned container. */
    public void set(T value) {
        versioned.set(value);
    }

    /** Creates a new independent reference to the same Versioned container. */
    public Reference<T> reference() {
        return versioned.createReference();
    }
}
