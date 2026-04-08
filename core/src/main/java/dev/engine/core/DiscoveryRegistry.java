package dev.engine.core;

import java.util.List;

/**
 * Generated per-module registry of {@link Discoverable} classes.
 *
 * <p>The annotation processor generates an implementation in each module
 * that contains {@code @Discoverable} classes. The implementation provides
 * hard references to all discoverable classes, ensuring they survive
 * TeaVM's dead code elimination.
 *
 * <p>Each implementation also calls initialization methods on generated
 * companion classes (e.g., {@code _NativeStruct.init()}) so that static
 * registration blocks execute.
 */
public interface DiscoveryRegistry {

    /** Returns all discoverable classes in this module. */
    List<Class<?>> classes();

    /** Triggers static initialization of generated companion classes. */
    void initialize();
}
