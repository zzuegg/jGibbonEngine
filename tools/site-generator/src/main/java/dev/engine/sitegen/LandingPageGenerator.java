package dev.engine.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Generates {@code _data/stats.json} for the landing page by reading
 * the output of other generators (modules.json, tutorial/example counts).
 */
public class LandingPageGenerator {

    public static void main(String[] args) throws IOException {
        var docsDir = args.length > 0 ? args[0] : "docs";
        var docsPath = Path.of(docsDir);
        var dataDir = docsPath.resolve("_data");
        Files.createDirectories(dataDir);

        // Read modules.json produced by ModulePageGenerator
        var modulesJsonPath = dataDir.resolve("modules.json");
        String modulesJson = Files.exists(modulesJsonPath) ? Files.readString(modulesJsonPath) : "{}";

        // Count modules by parsing the JSON (simple regex — we control the format)
        var moduleCount = countOccurrences(modulesJson, "\"slug\":");
        var featureCount = countFeatures(modulesJson);

        // Count backends (modules in the "Graphics Backend" category)
        var backendCount = countCategory(modulesJson, "Graphics Backend");

        // Count tutorials
        var tutorialsDir = docsPath.resolve("tutorials");
        var tutorialCount = 0;
        if (Files.exists(tutorialsDir)) {
            try (var list = Files.list(tutorialsDir)) {
                tutorialCount = (int) list
                        .filter(p -> p.toString().endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().equals("index.md"))
                        .count();
            }
        }

        // Count examples
        var examplesDir = docsPath.resolve("examples");
        var exampleCount = 0;
        if (Files.exists(examplesDir)) {
            try (var list = Files.list(examplesDir)) {
                exampleCount = (int) list
                        .filter(p -> p.toString().endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().equals("index.md"))
                        .count();
            }
        }

        // Count guides
        var guidesDir = docsPath.resolve("guides");
        var guideCount = 0;
        if (Files.exists(guidesDir)) {
            try (var list = Files.list(guidesDir)) {
                guideCount = (int) list
                        .filter(p -> p.toString().endsWith(".md"))
                        .filter(p -> !p.getFileName().toString().equals("index.md"))
                        .count();
            }
        }

        // Generate stats.json
        var stats = new StringBuilder();
        stats.append("{\n");
        stats.append("  \"javaVersion\": 25,\n");
        stats.append("  \"backendCount\": ").append(backendCount).append(",\n");
        stats.append("  \"moduleCount\": ").append(moduleCount).append(",\n");
        stats.append("  \"featureCount\": ").append(featureCount).append(",\n");
        stats.append("  \"tutorialCount\": ").append(tutorialCount).append(",\n");
        stats.append("  \"exampleCount\": ").append(exampleCount).append(",\n");
        stats.append("  \"guideCount\": ").append(guideCount).append("\n");
        stats.append("}\n");

        Files.writeString(dataDir.resolve("stats.json"), stats.toString());
        System.out.println("  GENERATED _data/stats.json");
        System.out.println("    modules=" + moduleCount + " features=" + featureCount +
                " backends=" + backendCount + " tutorials=" + tutorialCount +
                " examples=" + exampleCount + " guides=" + guideCount);
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(pattern, idx)) != -1) {
            count++;
            idx += pattern.length();
        }
        return count;
    }

    private static int countFeatures(String json) {
        // Count entries in the top-level "features" array
        var pattern = Pattern.compile("\"features\"\\s*:\\s*\\[([^\\]]*(?:\\{[^}]*\\}[^\\]]*)*)]",
                Pattern.DOTALL);
        var matcher = pattern.matcher(json);
        // Find the last "features" array (the top-level one, not per-module)
        int count = 0;
        while (matcher.find()) {
            var inner = matcher.group(1);
            if (inner.contains("\"name\"")) {
                count = countOccurrences(inner, "\"name\":");
            }
        }
        return count;
    }

    private static int countCategory(String json, String category) {
        var pattern = Pattern.compile("\"category\"\\s*:\\s*\"" + Pattern.quote(category) + "\"");
        var matcher = pattern.matcher(json);
        int count = 0;
        while (matcher.find()) count++;
        return count;
    }
}
