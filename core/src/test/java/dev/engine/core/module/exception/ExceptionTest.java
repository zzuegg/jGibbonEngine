package dev.engine.core.module.exception;

import dev.engine.core.module.ModuleState;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExceptionTest {

    @Test
    void moduleExceptionMessage() {
        ModuleException ex = new ModuleException("test error");
        assertEquals("test error", ex.getMessage());
    }

    @Test
    void moduleExceptionMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        ModuleException ex = new ModuleException("wrapped error", cause);
        assertEquals("wrapped error", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void cyclicDependencyExceptionPath() {
        List<String> path = List.of("ModuleA", "ModuleB", "ModuleC", "ModuleA");
        CyclicDependencyException ex = new CyclicDependencyException(path);

        assertEquals(path, ex.getCyclePath());
        assertTrue(ex.getMessage().contains("ModuleA"));
        assertTrue(ex.getMessage().contains("ModuleB"));
        assertTrue(ex.getMessage().contains("ModuleC"));
        assertTrue(ex.getMessage().contains("\u2192"));
    }

    @Test
    void cyclicDependencyExceptionPathIsUnmodifiable() {
        List<String> path = List.of("ModuleA", "ModuleB", "ModuleA");
        CyclicDependencyException ex = new CyclicDependencyException(path);

        assertThrows(UnsupportedOperationException.class, () -> ex.getCyclePath().add("ModuleX"));
    }

    @Test
    void missingDependencyFields() {
        MissingDependencyException ex = new MissingDependencyException(String.class, Integer.class);

        assertEquals(String.class, ex.getRequestingModule());
        assertEquals(Integer.class, ex.getMissingDependency());
        assertTrue(ex.getMessage().contains("String"));
        assertTrue(ex.getMessage().contains("Integer"));
    }

    @Test
    void invalidStateTransitionFields() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException(
                ModuleState.CREATED, ModuleState.RUNNING);

        assertEquals(ModuleState.CREATED, ex.getCurrentState());
        assertEquals(ModuleState.RUNNING, ex.getAttemptedState());
        assertTrue(ex.getMessage().contains("CREATED"));
        assertTrue(ex.getMessage().contains("RUNNING"));
    }

    @Test
    void invalidStateTransitionMessageFormat() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException(
                ModuleState.RUNNING, ModuleState.INITIALIZING);

        assertEquals("Invalid state transition from RUNNING to INITIALIZING", ex.getMessage());
    }

    @Test
    void dependentModuleFields() {
        Set<Class<?>> dependents = new HashSet<>();
        dependents.add(String.class);
        dependents.add(Integer.class);

        DependentModuleException ex = new DependentModuleException(Double.class, dependents);

        assertEquals(Double.class, ex.getModule());
        assertEquals(Set.of(String.class, Integer.class), ex.getDependents());
        assertTrue(ex.getMessage().contains("Double"));
    }

    @Test
    void dependentModuleSetIsUnmodifiable() {
        Set<Class<?>> dependents = new HashSet<>();
        dependents.add(String.class);

        DependentModuleException ex = new DependentModuleException(Double.class, dependents);

        assertThrows(UnsupportedOperationException.class, () -> ex.getDependents().add(Long.class));
    }

    @Test
    void dependentModuleMessageContainsDependents() {
        Set<Class<?>> dependents = new HashSet<>();
        dependents.add(String.class);

        DependentModuleException ex = new DependentModuleException(Integer.class, dependents);

        assertTrue(ex.getMessage().contains("Integer"));
        assertTrue(ex.getMessage().contains("String"));
    }
}
