package dev.engine.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * JVM-only resource loader for {@link Discovery}. Reads
 * {@code META-INF/services/dev.engine.core.DiscoveryRegistry} files
 * from the classpath and registers the listed implementations.
 *
 * <p>This class is loaded reflectively by {@link Discovery} so that
 * TeaVM's compiler never traces into {@code ClassLoader.getResources()},
 * which is not available on the web platform.
 */
public final class DiscoveryResourceLoader {

    private static final String RESOURCE = "META-INF/services/dev.engine.core.DiscoveryRegistry";

    private DiscoveryResourceLoader() {}

    /** Scans classpath resources and registers all discovered registries. */
    public static void load() {
        try {
            var cl = DiscoveryResourceLoader.class.getClassLoader();
            var resources = cl.getResources(RESOURCE);
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                try (var reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        try {
                            var cls = Class.forName(line);
                            var registry = (DiscoveryRegistry) cls.getDeclaredConstructor().newInstance();
                            Discovery.addRegistry(registry);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }
}
