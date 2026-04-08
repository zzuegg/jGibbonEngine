package dev.engine.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry of all {@link Discoverable} classes across all modules.
 *
 * <p>On JVM, registries are loaded automatically from
 * {@code META-INF/services/dev.engine.core.DiscoveryRegistry} resource files
 * (written by the annotation processor). On TeaVM, the generated
 * {@code DiscoveryBootstrap} adds registries via {@link #addRegistry}.
 *
 * <p>Initialization happens automatically on first use — no explicit call needed.
 */
public final class Discovery {

    private static final CopyOnWriteArrayList<DiscoveryRegistry> registries = new CopyOnWriteArrayList<>();
    private static volatile boolean initialized;

    private Discovery() {}

    /** Adds a registry and runs its initialization. */
    public static void addRegistry(DiscoveryRegistry registry) {
        registries.add(registry);
        registry.initialize();
    }

    /** Returns all discoverable classes from all loaded registries. */
    public static List<Class<?>> allClasses() {
        ensureInitialized();
        var all = new ArrayList<Class<?>>();
        for (var registry : registries) {
            all.addAll(registry.classes());
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * Ensures registries are loaded. On JVM, scans resource files written
     * by the annotation processor. Safe to call multiple times.
     */
    public static void ensureInitialized() {
        if (initialized) return;
        synchronized (Discovery.class) {
            if (initialized) return;
            initialized = true;
            // Try JVM resource scanning — fails gracefully on TeaVM
            try {
                var loader = Class.forName("dev.engine.core.DiscoveryResourceLoader");
                var method = loader.getMethod("load");
                method.invoke(null);
            } catch (Exception ignored) {
                // Not on JVM or resource loader not available — rely on addRegistry() calls
            }
        }
    }
}
