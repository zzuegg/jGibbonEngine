package dev.engine.tests.screenshot;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Discovers test scenes and comparison tests by scanning for static final fields
 * in classes under the {@code scenes} package.
 *
 * <p>Supports both file-system class directories and JAR files on the classpath.
 */
public class SceneDiscovery {

    public record DiscoveredScene(String category, String name, RenderTestScene scene, Tolerance tolerance) {}
    public record DiscoveredComparison(String category, String name, ComparisonTest test) {}

    private final List<DiscoveredScene> scenes = new ArrayList<>();
    private final List<DiscoveredComparison> comparisons = new ArrayList<>();

    public SceneDiscovery() {
        discover();
    }

    public List<DiscoveredScene> scenes() { return scenes; }
    public List<DiscoveredComparison> comparisons() { return comparisons; }

    private void discover() {
        var scenesPackage = "dev.engine.tests.screenshot.scenes";
        var classLoader = getClass().getClassLoader();

        try {
            var packagePath = scenesPackage.replace('.', '/');
            var resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                var url = resources.nextElement();
                if ("file".equals(url.getProtocol())) {
                    scanDirectory(new File(url.toURI()), scenesPackage);
                } else if ("jar".equals(url.getProtocol())) {
                    scanJar(((JarURLConnection) url.openConnection()).getJarFile(), packagePath, scenesPackage);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to discover scenes", e);
        }
    }

    private void scanDirectory(File dir, String packageName) {
        if (!dir.exists()) return;
        for (var file : dir.listFiles()) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                var className = packageName + "." + file.getName().replace(".class", "");
                try {
                    var clazz = Class.forName(className);
                    scanClass(clazz, categoryFromPackage(packageName));
                } catch (ClassNotFoundException ignored) {}
            }
        }
    }

    private void scanJar(JarFile jar, String packagePath, String basePackage) {
        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            var name = entry.getName();
            if (name.startsWith(packagePath) && name.endsWith(".class") && !name.contains("$")) {
                var className = name.replace('/', '.').replace(".class", "");
                var packageName = className.substring(0, className.lastIndexOf('.'));
                try {
                    var clazz = Class.forName(className);
                    scanClass(clazz, categoryFromPackage(packageName));
                } catch (ClassNotFoundException ignored) {}
            }
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
                    var tolerance = findTolerance(clazz, field.getName());
                    scenes.add(new DiscoveredScene(category, name, scene, tolerance));
                } else if (ComparisonTest.class.isAssignableFrom(field.getType())) {
                    var test = (ComparisonTest) field.get(null);
                    var name = fieldNameToTestName(field.getName());
                    comparisons.add(new DiscoveredComparison(category, name, test));
                }
            } catch (IllegalAccessException ignored) {}
        }
    }

    private Tolerance findTolerance(Class<?> clazz, String sceneName) {
        // Look for SCENE_NAME_TOLERANCE companion field
        try {
            var toleranceField = clazz.getDeclaredField(sceneName + "_TOLERANCE");
            if (isStaticFinal(toleranceField) && toleranceField.getType() == Tolerance.class) {
                toleranceField.setAccessible(true);
                return (Tolerance) toleranceField.get(null);
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}

        // Look for class-level DEFAULT_TOLERANCE
        try {
            var defaultField = clazz.getDeclaredField("DEFAULT_TOLERANCE");
            if (isStaticFinal(defaultField) && defaultField.getType() == Tolerance.class) {
                defaultField.setAccessible(true);
                return (Tolerance) defaultField.get(null);
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}

        return Tolerance.loose();
    }

    private static boolean isStaticFinal(Field field) {
        int mods = field.getModifiers();
        return Modifier.isStatic(mods) && Modifier.isFinal(mods);
    }

    private static String fieldNameToTestName(String fieldName) {
        return fieldName.toLowerCase();
    }

    private static String categoryFromPackage(String packageName) {
        // dev.engine.tests.screenshot.scenes.basic → Basic
        var parts = packageName.split("\\.");
        var last = parts[parts.length - 1];
        if ("scenes".equals(last)) return "General";
        return last.substring(0, 1).toUpperCase() + last.substring(1);
    }
}
