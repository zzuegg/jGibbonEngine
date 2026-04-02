package dev.engine.core.module;

/**
 * Represents the lifecycle state of a game module.
 *
 * <p>States progress in a linear sequence and only forward transitions are permitted.
 * Use {@link #canTransitionTo(ModuleState)} to validate a state change before applying it.
 *
 * <p>Valid transitions:
 * <ul>
 *   <li>CREATED -> INITIALIZING</li>
 *   <li>INITIALIZING -> RUNNING</li>
 *   <li>RUNNING -> DEINITIALIZING</li>
 *   <li>DEINITIALIZING -> CLEANED_UP</li>
 * </ul>
 */
public enum ModuleState {

    /** Module has been created but not yet initialised. */
    CREATED,

    /** Module is currently running its initialisation logic. */
    INITIALIZING,

    /** Module is fully initialised and active. */
    RUNNING,

    /** Module is currently running its de-initialisation logic. */
    DEINITIALIZING,

    /** Module has been fully de-initialised and all resources released. */
    CLEANED_UP;

    /**
     * The single state that is permitted to follow this state, or {@code null} if this state
     * is terminal ({@link #CLEANED_UP}).
     *
     * <p>Populated lazily on first access to avoid enum forward-reference issues.
     */
    private ModuleState allowedNext;

    /**
     * Returns {@code true} if transitioning from this state to {@code next} is permitted.
     *
     * @param next the target state to transition to; must not be {@code null}
     * @return {@code true} if the transition is valid, {@code false} otherwise
     */
    public boolean canTransitionTo(ModuleState next) {
        return resolvedAllowedNext() == next;
    }

    /**
     * Resolves and caches the allowed next state for this constant.
     * Forward references among enum constants are handled here rather than in the
     * constructor, where self-referential initialisation would fail to compile.
     */
    private ModuleState resolvedAllowedNext() {
        if (this == CLEANED_UP) {
            return null;
        }
        if (allowedNext == null) {
            allowedNext = values()[ordinal() + 1];
        }
        return allowedNext;
    }
}
