package dev.engine.tests.screenshot.scenes;

import dev.engine.core.Discovery;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers test scenes and comparison tests from {@code @Discoverable} classes
 * via {@link Discovery}. Scans static final fields in discovered classes for
 * {@link RenderTestScene} and {@link ComparisonTest} instances.
 *
 * <p>Works on both JVM and TeaVM — no classpath scanning or Class.forName() needed.
 */
public class SceneRegistry {

    public record DiscoveredScene(String category, String name, String className,
                                   String fieldName, RenderTestScene scene) {}

    public record DiscoveredComparison(String category, String name, String className,
                                        String fieldName, ComparisonTest test) {}

    private final List<DiscoveredScene> scenes = new ArrayList<>();
    private final List<DiscoveredComparison> comparisons = new ArrayList<>();

    public SceneRegistry() {
        discover();
    }

    public List<DiscoveredScene> scenes() { return scenes; }
    public List<DiscoveredComparison> comparisons() { return comparisons; }

    private void discover() {
        Discovery.ensureInitialized();
        for (var cls : Discovery.allClasses()) {
            var category = categoryFromClass(cls);
            scanClass(cls, category);
        }
    }

    private void scanClass(Class<?> clazz, String category) {
        for (var field : clazz.getDeclaredFields()) {
            if (!isStaticFinal(field)) continue;
            field.setAccessible(true);

            try {
                if (RenderTestScene.class.isAssignableFrom(field.getType())) {
                    var scene = (RenderTestScene) field.get(null);
                    var name = fieldNameToTestName(field.getName());
                    scenes.add(new DiscoveredScene(category, name,
                            clazz.getName(), field.getName(), scene));
                } else if (ComparisonTest.class.isAssignableFrom(field.getType())) {
                    var test = (ComparisonTest) field.get(null);
                    var name = fieldNameToTestName(field.getName());
                    comparisons.add(new DiscoveredComparison(category, name,
                            clazz.getName(), field.getName(), test));
                }
            } catch (IllegalAccessException ignored) {}
        }
    }

    private static boolean isStaticFinal(Field field) {
        int mods = field.getModifiers();
        return Modifier.isStatic(mods) && Modifier.isFinal(mods);
    }

    private static String fieldNameToTestName(String fieldName) {
        return fieldName.toLowerCase();
    }

    private static String categoryFromClass(Class<?> cls) {
        var pkg = cls.getPackageName();
        var parts = pkg.split("\\.");
        var last = parts[parts.length - 1];
        if ("scenes".equals(last)) return "General";
        return last.substring(0, 1).toUpperCase() + last.substring(1);
    }
}
