package dev.engine.core.module;

import dev.engine.core.module.exception.DependentModuleException;
import dev.engine.core.module.exception.MissingDependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Central manager responsible for the lifecycle and update orchestration of
 * {@link Module} instances.
 *
 * <p>Modules are registered via {@link #add(Module)}, which validates dependencies,
 * updates the internal {@link DependencyGraph}, and runs the create/init lifecycle.
 * Each {@link #tick(double)} or {@link #update(Object)} call fans out to all enabled
 * modules in dependency-safe parallel levels using the configured {@link Executor}.
 *
 * @param <T> the update context type passed to modules
 */
public class ModuleManager<T extends Time> {

    private static final Logger log = LoggerFactory.getLogger(ModuleManager.class);

    private final UpdateStrategy<T> strategy;
    private final Executor executor;
    private final DependencyGraph graph = new DependencyGraph();
    private final Map<Class<?>, Module<T>> modules = new ConcurrentHashMap<>();
    private volatile boolean shutdown = false;
    private final Object lifecycleLock = new Object();

    public ModuleManager(UpdateStrategy<T> strategy, Executor executor) {
        this.strategy = strategy;
        this.executor = executor;
    }

    // -- Module lifecycle --

    public void add(Module<T> module) {
        synchronized (lifecycleLock) {
            if (shutdown) {
                throw new IllegalStateException("ModuleManager has been shut down");
            }

            Class<?> moduleType = module.getClass();
            @SuppressWarnings("unchecked")
            Set<Class<? extends Module<T>>> typedDeps = module.getDependencies();
            Set<Class<?>> deps = Set.copyOf(typedDeps);

            // Add to graph — may throw CyclicDependencyException or IllegalArgumentException
            graph.add(moduleType, deps);

            // Validate all dependencies are present and running
            for (Class<?> dep : deps) {
                Module<T> depModule = modules.get(dep);
                if (depModule == null) {
                    graph.remove(moduleType);
                    throw new MissingDependencyException(moduleType, dep);
                }
                if (depModule.getState() != ModuleState.RUNNING) {
                    graph.remove(moduleType);
                    throw new IllegalStateException(
                            "Dependency " + dep.getSimpleName() + " is not in RUNNING state");
                }
            }

            // Store in map and run lifecycle
            modules.put(moduleType, module);
            try {
                module.onCreate();
                module.onInit(this);
            } catch (Exception e) {
                modules.remove(moduleType);
                graph.remove(moduleType);
                throw e;
            }
            log.info("Module {} added", moduleType.getSimpleName());
        }
    }

    public void remove(Class<? extends Module<T>> moduleType, RemovalPolicy policy) {
        synchronized (lifecycleLock) {
            if (shutdown) {
                throw new IllegalStateException("ModuleManager has been shut down");
            }

            Module<T> module = modules.get(moduleType);
            if (module == null) {
                throw new IllegalArgumentException(
                        "Module not registered: " + moduleType.getSimpleName());
            }

            Set<Class<?>> dependents = graph.getDependents(moduleType);

            if (policy == RemovalPolicy.REJECT && !dependents.isEmpty()) {
                throw new DependentModuleException(moduleType, dependents);
            }

            if (policy == RemovalPolicy.CASCADE && !dependents.isEmpty()) {
                Set<Class<?>> transitiveDependents = graph.getTransitiveDependents(moduleType);

                // Collect all modules to remove: the target + all transitive dependents
                Set<Class<?>> toRemove = new java.util.LinkedHashSet<>(transitiveDependents);
                toRemove.add(moduleType);

                // Get cleanup order for the entire graph, then filter to only affected modules
                List<Class<?>> fullCleanupOrder = graph.getCleanupOrder();
                List<Class<?>> removalOrder = new ArrayList<>();
                for (Class<?> type : fullCleanupOrder) {
                    if (toRemove.contains(type)) {
                        removalOrder.add(type);
                    }
                }

                for (Class<?> type : removalOrder) {
                    Module<T> mod = modules.get(type);
                    if (mod != null) {
                        mod.onDeinit();
                        mod.onCleanup();
                        modules.remove(type);
                        graph.remove(type);
                        log.info("Module {} removed (cascade)", type.getSimpleName());
                    }
                }
            } else {
                // No dependents — remove just this module
                module.onDeinit();
                module.onCleanup();
                modules.remove(moduleType);
                graph.remove(moduleType);
                log.info("Module {} removed", moduleType.getSimpleName());
            }
        }
    }

    public void shutdown() {
        synchronized (lifecycleLock) {
            if (shutdown) {
                return;
            }
            shutdown = true;

            List<Class<?>> cleanupOrder = graph.getCleanupOrder();
            for (Class<?> type : cleanupOrder) {
                Module<T> module = modules.get(type);
                if (module != null && module.getState() == ModuleState.RUNNING) {
                    try {
                        module.onDeinit();
                        module.onCleanup();
                    } catch (Exception e) {
                        log.warn("Exception during shutdown of module {}: {}",
                                type.getSimpleName(), e.getMessage(), e);
                    }
                }
            }

            modules.clear();
            log.info("ModuleManager shut down");
        }
    }

    // -- Module lookup --

    @SuppressWarnings("unchecked")
    public <M extends Module<T>> M getModule(Class<M> type) {
        return (M) modules.get(type);
    }

    public boolean hasModule(Class<? extends Module<T>> type) {
        return modules.containsKey(type);
    }

    public Collection<Module<T>> getModules() {
        return Collections.unmodifiableCollection(modules.values());
    }

    // -- Update --

    public void tick(double elapsedSeconds) {
        if (shutdown) {
            return;
        }
        strategy.advance(elapsedSeconds, this::updateAll);
    }

    public void update(T context) {
        if (shutdown) {
            return;
        }
        updateAll(context);
    }

    // -- Internal --

    private void updateAll(T context) {
        List<Set<Class<?>>> levels = graph.getParallelLevels();
        log.debug("Updating {} parallel levels", levels.size());

        for (Set<Class<?>> level : levels) {
            List<Module<T>> updatable = new ArrayList<>();
            for (Class<?> type : level) {
                Module<T> module = modules.get(type);
                if (module != null
                        && module.getState() == ModuleState.RUNNING
                        && module.isEnabled()) {
                    updatable.add(module);
                }
            }

            if (updatable.isEmpty()) {
                continue;
            }

            if (updatable.size() == 1) {
                Module<T> module = updatable.getFirst();
                log.trace("Updating module {} (direct)", module.getClass().getSimpleName());
                try {
                    module.onUpdate(context);
                } catch (Exception e) {
                    log.warn("Exception during update of module {}: {}",
                            module.getClass().getSimpleName(), e.getMessage(), e);
                }
            } else {
                CompletableFuture<?>[] futures = new CompletableFuture[updatable.size()];
                for (int i = 0; i < updatable.size(); i++) {
                    Module<T> module = updatable.get(i);
                    log.trace("Updating module {} (parallel)", module.getClass().getSimpleName());
                    futures[i] = CompletableFuture.runAsync(() -> {
                        try {
                            module.onUpdate(context);
                        } catch (Exception e) {
                            log.warn("Exception during update of module {}: {}",
                                    module.getClass().getSimpleName(), e.getMessage(), e);
                        }
                    }, executor);
                }
                CompletableFuture.allOf(futures).join();
            }
        }
    }
}
