package dev.engine.core.module;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleStateTest {

    @Test
    void createdCanTransitionToInitializing() {
        assertTrue(ModuleState.CREATED.canTransitionTo(ModuleState.INITIALIZING));
    }

    @Test
    void initializingCanTransitionToRunning() {
        assertTrue(ModuleState.INITIALIZING.canTransitionTo(ModuleState.RUNNING));
    }

    @Test
    void runningCanTransitionToDeinitializing() {
        assertTrue(ModuleState.RUNNING.canTransitionTo(ModuleState.DEINITIALIZING));
    }

    @Test
    void deinitializingCanTransitionToCleanedUp() {
        assertTrue(ModuleState.DEINITIALIZING.canTransitionTo(ModuleState.CLEANED_UP));
    }

    @Test
    void cleanedUpCannotTransitionAnywhere() {
        assertFalse(ModuleState.CLEANED_UP.canTransitionTo(ModuleState.CREATED));
        assertFalse(ModuleState.CLEANED_UP.canTransitionTo(ModuleState.INITIALIZING));
        assertFalse(ModuleState.CLEANED_UP.canTransitionTo(ModuleState.RUNNING));
        assertFalse(ModuleState.CLEANED_UP.canTransitionTo(ModuleState.DEINITIALIZING));
        assertFalse(ModuleState.CLEANED_UP.canTransitionTo(ModuleState.CLEANED_UP));
    }

    @Test
    void cannotSkipStates() {
        assertFalse(ModuleState.CREATED.canTransitionTo(ModuleState.RUNNING));
        assertFalse(ModuleState.CREATED.canTransitionTo(ModuleState.DEINITIALIZING));
        assertFalse(ModuleState.CREATED.canTransitionTo(ModuleState.CLEANED_UP));
        assertFalse(ModuleState.INITIALIZING.canTransitionTo(ModuleState.DEINITIALIZING));
        assertFalse(ModuleState.INITIALIZING.canTransitionTo(ModuleState.CLEANED_UP));
        assertFalse(ModuleState.RUNNING.canTransitionTo(ModuleState.CLEANED_UP));
    }

    @Test
    void cannotGoBackwards() {
        assertFalse(ModuleState.RUNNING.canTransitionTo(ModuleState.CREATED));
        assertFalse(ModuleState.RUNNING.canTransitionTo(ModuleState.INITIALIZING));
        assertFalse(ModuleState.DEINITIALIZING.canTransitionTo(ModuleState.CREATED));
        assertFalse(ModuleState.DEINITIALIZING.canTransitionTo(ModuleState.INITIALIZING));
        assertFalse(ModuleState.DEINITIALIZING.canTransitionTo(ModuleState.RUNNING));
        assertFalse(ModuleState.CLEANED_UP.canTransitionTo(ModuleState.CREATED));
    }

    @Test
    void cannotTransitionToSelf() {
        assertFalse(ModuleState.CREATED.canTransitionTo(ModuleState.CREATED));
        assertFalse(ModuleState.INITIALIZING.canTransitionTo(ModuleState.INITIALIZING));
        assertFalse(ModuleState.RUNNING.canTransitionTo(ModuleState.RUNNING));
        assertFalse(ModuleState.DEINITIALIZING.canTransitionTo(ModuleState.DEINITIALIZING));
        assertFalse(ModuleState.CLEANED_UP.canTransitionTo(ModuleState.CLEANED_UP));
    }
}
