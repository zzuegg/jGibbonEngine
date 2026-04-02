package dev.engine.core.module;

import java.util.Set;

/**
 * Contract for a managed module with a typed update context.
 *
 * <p>Lifecycle: create -> init -> [update]* -> deinit -> cleanup
 *
 * @param <T> the update context type
 */
public interface Module<T extends Time> {

    /** Called once after registration. Allocate resources here. */
    void onCreate();

    /**
     * Called once after all dependencies are initialized.
     *
     * @param manager the module manager that owns this module
     */
    void onInit(ModuleManager<T> manager);

    /**
     * Called per update tick with the strategy-produced context.
     *
     * @param context the update context for this tick
     */
    void onUpdate(T context);

    /** Called once before cleanup. Reverse of init. */
    void onDeinit();

    /** Called once to release all resources. */
    void onCleanup();

    /** Called when the module is enabled (default no-op). */
    default void onEnable() {}

    /** Called when the module is disabled (default no-op). */
    default void onDisable() {}

    /**
     * Returns the set of module types this module depends on.
     * Dependencies must be initialized before this module.
     *
     * @return an unmodifiable set of module classes this module depends on; empty by default
     */
    default Set<Class<? extends Module<T>>> getDependencies() {
        return Set.of();
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return the module's current {@link ModuleState}
     */
    ModuleState getState();

    /**
     * Returns whether this module is currently enabled for updates.
     *
     * @return {@code true} if the module is enabled, {@code false} otherwise
     */
    boolean isEnabled();
}
