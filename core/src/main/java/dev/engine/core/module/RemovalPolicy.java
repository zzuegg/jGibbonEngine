package dev.engine.core.module;

/**
 * Policy for handling dependent modules when a module is removed.
 */
public enum RemovalPolicy {

    /** Reject removal if other modules depend on the target. */
    REJECT,

    /** Cascade removal to all transitive dependents. */
    CASCADE
}
