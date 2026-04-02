package dev.engine.core.module;

import dev.engine.core.module.exception.InvalidStateTransitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Template-method base class for modules that enforces the lifecycle state machine.
 *
 * <p>Subclasses override the {@code do*} methods to provide custom behaviour at each
 * lifecycle stage. All public lifecycle methods are {@code final} so that the state
 * machine cannot be circumvented.
 *
 * <p>The only method a subclass <em>must</em> implement is {@link #doUpdate(Object)};
 * every other hook defaults to a no-op.
 *
 * @param <T> the update context type
 */
public abstract class AbstractModule<T extends Time> implements Module<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractModule.class);

    /** Thread-safe lifecycle state. */
    private volatile ModuleState state = ModuleState.CREATED;

    /** Whether this module should receive update ticks. Starts enabled. */
    private volatile boolean enabled = true;

    /** Back-reference to the owning manager, set during initialisation. */
    private ModuleManager<T> manager;

    // -- Public final lifecycle methods --

    @Override
    public final void onCreate() {
        if (state != ModuleState.CREATED) {
            throw new InvalidStateTransitionException(state, ModuleState.CREATED);
        }
        doCreate();
    }

    @Override
    public final void onInit(ModuleManager<T> manager) {
        transitionTo(ModuleState.INITIALIZING);
        this.manager = manager;
        doInit(manager);
        transitionTo(ModuleState.RUNNING);
    }

    @Override
    public final void onUpdate(T context) {
        if (state != ModuleState.RUNNING) {
            throw new InvalidStateTransitionException(state, ModuleState.RUNNING);
        }
        if (!enabled) {
            return;
        }
        doUpdate(context);
    }

    @Override
    public final void onDeinit() {
        transitionTo(ModuleState.DEINITIALIZING);
        doDeinit();
    }

    @Override
    public final void onCleanup() {
        transitionTo(ModuleState.CLEANED_UP);
        doCleanup();
        manager = null;
    }

    // -- Enable / disable --

    public final void setEnabled(boolean enabled) {
        if (state != ModuleState.RUNNING) {
            throw new InvalidStateTransitionException(state, state);
        }
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    // -- Template methods (for subclasses) --

    protected void doCreate() {}

    protected void doInit(ModuleManager<T> manager) {}

    protected abstract void doUpdate(T context);

    protected void doDeinit() {}

    protected void doCleanup() {}

    // -- Convenience --

    protected final <M extends Module<T>> M getModule(Class<M> type) {
        if (manager == null) {
            throw new IllegalStateException("Module not yet initialised — manager is not available");
        }
        return manager.getModule(type);
    }

    // -- Getters --

    @Override
    public final ModuleState getState() {
        return state;
    }

    @Override
    public final boolean isEnabled() {
        return enabled;
    }

    // -- Internal --

    private void transitionTo(ModuleState next) {
        if (!state.canTransitionTo(next)) {
            throw new InvalidStateTransitionException(state, next);
        }
        log.info("Module {} transitioning to {}", getClass().getSimpleName(), next);
        state = next;
    }
}
