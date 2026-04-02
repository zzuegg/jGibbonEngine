package dev.engine.core.module;

import dev.engine.core.module.exception.InvalidStateTransitionException;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractModuleTest {

    private static class TestModule extends AbstractModule<Time> {
        boolean createCalled = false;
        boolean initCalled = false;
        List<Time> updateContexts = new ArrayList<>();
        boolean deinitCalled = false;
        boolean cleanupCalled = false;
        boolean enableCalled = false;
        boolean disableCalled = false;

        @Override
        protected void doCreate() {
            createCalled = true;
        }

        @Override
        protected void doInit(ModuleManager<Time> m) {
            initCalled = true;
        }

        @Override
        protected void doUpdate(Time context) {
            updateContexts.add(context);
        }

        @Override
        protected void doDeinit() {
            deinitCalled = true;
        }

        @Override
        protected void doCleanup() {
            cleanupCalled = true;
        }

        @Override
        public void onEnable() {
            enableCalled = true;
        }

        @Override
        public void onDisable() {
            disableCalled = true;
        }
    }

    private TestModule module;
    private ModuleManager<Time> manager;

    @BeforeEach
    void setUp() {
        module = new TestModule();
        manager = new ModuleManager<>(new ManualUpdate<>(), Runnable::run);
    }

    @Test
    void initialStateIsCreated() {
        assertEquals(ModuleState.CREATED, module.getState());
    }

    @Test
    void initiallyEnabled() {
        assertTrue(module.isEnabled());
    }

    @Test
    void fullLifecycle() {
        module.onCreate();
        assertEquals(ModuleState.CREATED, module.getState());

        module.onInit(manager);
        assertEquals(ModuleState.RUNNING, module.getState());

        Time ctx = new Time(1, 1.0);
        module.onUpdate(ctx);
        assertEquals(1, module.updateContexts.size());
        assertEquals(ctx, module.updateContexts.get(0));

        module.onDeinit();
        assertEquals(ModuleState.DEINITIALIZING, module.getState());

        module.onCleanup();
        assertEquals(ModuleState.CLEANED_UP, module.getState());

        assertTrue(module.createCalled);
        assertTrue(module.initCalled);
        assertTrue(module.deinitCalled);
        assertTrue(module.cleanupCalled);
    }

    @Test
    void onCreateCalledInCreatedState() {
        module.onCreate();
        assertTrue(module.createCalled);
        assertEquals(ModuleState.CREATED, module.getState());
    }

    @Test
    void onInitTransitionsToRunning() {
        module.onInit(manager);
        assertEquals(ModuleState.RUNNING, module.getState());
        assertTrue(module.initCalled);
    }

    @Test
    void onUpdateCallsDoUpdate() {
        module.onInit(manager);
        Time ctx = new Time(42, 0.016);
        module.onUpdate(ctx);
        assertEquals(1, module.updateContexts.size());
        assertEquals(ctx, module.updateContexts.get(0));
    }

    @Test
    void onUpdateSkipsWhenDisabled() {
        module.onInit(manager);
        module.setEnabled(false);
        module.onUpdate(new Time(42, 0.016));
        assertTrue(module.updateContexts.isEmpty());
    }

    @Test
    void onDeinitTransitionsToDeinitializing() {
        module.onInit(manager);
        module.onDeinit();
        assertEquals(ModuleState.DEINITIALIZING, module.getState());
        assertTrue(module.deinitCalled);
    }

    @Test
    void onCleanupTransitionsToCleanedUp() {
        module.onInit(manager);
        module.onDeinit();
        module.onCleanup();
        assertEquals(ModuleState.CLEANED_UP, module.getState());
        assertTrue(module.cleanupCalled);
    }

    @Test
    void doubleCreateThrows() {
        module.onCreate();
        module.onInit(manager);
        assertThrows(InvalidStateTransitionException.class, () -> module.onCreate());
    }

    @Test
    void initWithoutCreateThrows() {
        module.onInit(manager);
        assertEquals(ModuleState.RUNNING, module.getState());
        assertThrows(InvalidStateTransitionException.class, () -> module.onInit(manager));
    }

    @Test
    void updateBeforeInitThrows() {
        assertThrows(InvalidStateTransitionException.class, () -> module.onUpdate(new Time(0, 1.0)));
    }

    @Test
    void updateAfterDeinitThrows() {
        module.onInit(manager);
        module.onDeinit();
        assertThrows(InvalidStateTransitionException.class, () -> module.onUpdate(new Time(0, 1.0)));
    }

    @Test
    void setEnabledOnlyWorksInRunning() {
        assertThrows(InvalidStateTransitionException.class, () -> module.setEnabled(false));
    }

    @Test
    void enableDisableCallbacks() {
        module.onInit(manager);
        module.setEnabled(false);
        assertTrue(module.disableCalled);
        assertFalse(module.enableCalled);

        module.setEnabled(true);
        assertTrue(module.enableCalled);
    }

    @Test
    void setEnabledNoOpWhenSameState() {
        module.onInit(manager);
        module.setEnabled(true);
        assertFalse(module.enableCalled);
        assertFalse(module.disableCalled);

        module.setEnabled(false);
        assertTrue(module.disableCalled);
        module.disableCalled = false;

        module.setEnabled(false);
        assertFalse(module.disableCalled);
    }

    @Test
    void getModuleThrowsBeforeInit() {
        assertThrows(IllegalStateException.class, () -> module.getModule(TestModule.class));
    }
}
