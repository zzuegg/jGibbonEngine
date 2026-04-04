package dev.engine.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Generates module overview pages from {@code @EngineModule} annotations
 * on {@code package-info.java} files, and {@code @EngineFeature} annotations.
 *
 * <p>Outputs:
 * <ul>
 *   <li>{@code docs/modules/index.md} — module overview grouped by category</li>
 *   <li>{@code docs/modules/<slug>.md} — per-module detail page</li>
 *   <li>{@code docs/_data/modules.json} — structured data for Jekyll templates</li>
 * </ul>
 */
public class ModulePageGenerator {

    record ModuleInfo(String name, String description, String category,
                      List<String> features, String icon, String slug) {}

    record FeatureInfo(String name, String description, String icon) {}

    public static void main(String[] args) throws IOException {
        var rootDir = args.length > 0 ? args[0] : ".";
        var docsDir = args.length > 1 ? args[1] : "docs";

        var modules = new ArrayList<ModuleInfo>();
        var engineFeatures = new ArrayList<FeatureInfo>();

        // Scan all package-info.java files in the project
        try (var walk = Files.walk(Path.of(rootDir))) {
            walk.filter(p -> p.getFileName().toString().equals("package-info.java"))
                .filter(p -> p.toString().contains("src/main/java"))
                .sorted()
                .forEach(p -> {
                    try {
                        var source = Files.readString(p);
                        parseModule(source, modules);
                        parseFeatures(source, engineFeatures);
                    } catch (IOException e) {
                        System.err.println("Error parsing " + p + ": " + e.getMessage());
                    }
                });
        }

        // Also scan for @EngineFeature on regular .java files
        try (var walk = Files.walk(Path.of(rootDir))) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> p.toString().contains("src/main/java"))
                .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                .filter(p -> !p.toString().contains("tools/site-generator"))
                .sorted()
                .forEach(p -> {
                    try {
                        var source = Files.readString(p);
                        parseFeatures(source, engineFeatures);
                    } catch (IOException e) {
                        System.err.println("Error parsing " + p + ": " + e.getMessage());
                    }
                });
        }

        modules.sort(Comparator.comparing(ModuleInfo::category)
                .thenComparing(ModuleInfo::name));

        // Generate output
        var modulesDir = Path.of(docsDir, "modules");
        var dataDir = Path.of(docsDir, "_data");
        Files.createDirectories(modulesDir);
        Files.createDirectories(dataDir);

        // Per-module pages
        for (var mod : modules) {
            Files.writeString(modulesDir.resolve(mod.slug + ".md"), generateModulePage(mod));
            System.out.println("  GENERATED modules/" + mod.slug + ".md — " + mod.name);
        }

        // Module index page
        Files.writeString(modulesDir.resolve("index.md"), generateIndex(modules));
        System.out.println("  GENERATED modules/index.md");

        // JSON data for Jekyll
        Files.writeString(dataDir.resolve("modules.json"), generateModulesJson(modules, engineFeatures));
        System.out.println("  GENERATED _data/modules.json");

        System.out.println("\n" + modules.size() + " modules, " +
                engineFeatures.size() + " features generated");
    }

    private static void parseModule(String source, List<ModuleInfo> modules) {
        var body = AnnotationParser.extractAnnotationBody(source, "EngineModule");
        if (body == null) return;

        var name = AnnotationParser.extractString(body, "name");
        var description = AnnotationParser.extractString(body, "description");
        var category = AnnotationParser.extractString(body, "category");
        var features = AnnotationParser.extractStringArray(body, "features");
        var icon = AnnotationParser.extractString(body, "icon");

        if (name == null || category == null) return;

        var slug = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("-+$", "");
        modules.add(new ModuleInfo(name, description != null ? description : "",
                category, features, icon != null ? icon : "", slug));
    }

    private static void parseFeatures(String source, List<FeatureInfo> features) {
        for (var body : AnnotationParser.extractAllAnnotationBodies(source, "EngineFeature")) {
            var name = AnnotationParser.extractString(body, "name");
            var description = AnnotationParser.extractString(body, "description");
            var icon = AnnotationParser.extractString(body, "icon");

            if (name == null) continue;

            // Avoid duplicates
            var finalName = name;
            if (features.stream().noneMatch(f -> f.name().equals(finalName))) {
                features.add(new FeatureInfo(name,
                        description != null ? description : "",
                        icon != null ? icon : ""));
            }
        }
    }

    private static String generateModulePage(ModuleInfo mod) {
        var sb = new StringBuilder();
        sb.append("---\n");
        sb.append("layout: page\n");
        sb.append("title: \"").append(mod.name).append("\"\n");
        sb.append("description: \"").append(mod.description).append("\"\n");
        sb.append("---\n\n");

        sb.append("<span class=\"index-card-label\">").append(mod.category).append("</span>\n\n");
        sb.append(mod.description).append("\n\n");

        if (!mod.features.isEmpty()) {
            sb.append("## Features\n\n");
            for (var feature : mod.features) {
                sb.append("- ").append(feature).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static String generateIndex(List<ModuleInfo> modules) {
        var sb = new StringBuilder();
        sb.append("---\n");
        sb.append("layout: page\n");
        sb.append("title: \"Modules\"\n");
        sb.append("description: \"Engine module overview — architecture at a glance.\"\n");
        sb.append("---\n\n");

        var byCategory = new LinkedHashMap<String, List<ModuleInfo>>();
        for (var m : modules) {
            byCategory.computeIfAbsent(m.category, k -> new ArrayList<>()).add(m);
        }

        for (var entry : byCategory.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");
            sb.append("<div class=\"index-grid\">\n");
            for (var m : entry.getValue()) {
                sb.append("  <a class=\"index-card\" href=\"{{ site.baseurl }}/modules/")
                  .append(m.slug).append("\">\n");
                if (!m.icon.isEmpty()) {
                    sb.append("    <span class=\"feature-icon\">").append(m.icon).append("</span>\n");
                }
                sb.append("    <h3>").append(m.name).append("</h3>\n");
                sb.append("    <p>").append(m.description).append("</p>\n");
                sb.append("  </a>\n");
            }
            sb.append("</div>\n\n");
        }

        return sb.toString();
    }

    private static String generateModulesJson(List<ModuleInfo> modules, List<FeatureInfo> features) {
        var sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"modules\": [\n");
        for (int i = 0; i < modules.size(); i++) {
            var m = modules.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": ").append(jsonString(m.name)).append(",\n");
            sb.append("      \"description\": ").append(jsonString(m.description)).append(",\n");
            sb.append("      \"category\": ").append(jsonString(m.category)).append(",\n");
            sb.append("      \"icon\": ").append(jsonString(m.icon)).append(",\n");
            sb.append("      \"slug\": ").append(jsonString(m.slug)).append(",\n");
            sb.append("      \"features\": [");
            for (int j = 0; j < m.features.size(); j++) {
                if (j > 0) sb.append(", ");
                sb.append(jsonString(m.features.get(j)));
            }
            sb.append("]\n");
            sb.append("    }");
            if (i < modules.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"features\": [\n");
        for (int i = 0; i < features.size(); i++) {
            var f = features.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": ").append(jsonString(f.name())).append(",\n");
            sb.append("      \"description\": ").append(jsonString(f.description())).append(",\n");
            sb.append("      \"icon\": ").append(jsonString(f.icon())).append("\n");
            sb.append("    }");
            if (i < features.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
