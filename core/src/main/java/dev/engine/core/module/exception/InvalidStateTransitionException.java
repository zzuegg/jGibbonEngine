package dev.engine.core.module.exception;

import dev.engine.core.module.ModuleState;

public class InvalidStateTransitionException extends ModuleException {

    private final ModuleState currentState;
    private final ModuleState attemptedState;

    public InvalidStateTransitionException(ModuleState currentState, ModuleState attemptedState) {
        super(buildMessage(currentState, attemptedState));
        this.currentState = currentState;
        this.attemptedState = attemptedState;
    }

    public ModuleState getCurrentState() {
        return currentState;
    }

    public ModuleState getAttemptedState() {
        return attemptedState;
    }

    private static String buildMessage(ModuleState currentState, ModuleState attemptedState) {
        return "Invalid state transition from " + currentState.name() + " to " + attemptedState.name();
    }
}
