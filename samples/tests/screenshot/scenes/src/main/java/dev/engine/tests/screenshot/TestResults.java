package dev.engine.tests.screenshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Collects per-scene diff results during test execution and writes them
 * to a JSON file for the report generator to read.
 */
public class TestResults {

    private static final TestResults INSTANCE = new TestResults();
    private final Map<String, Map<String, Double>> diffs = new ConcurrentHashMap<>();

    public static TestResults instance() { return INSTANCE; }

    public void recordDiff(String sceneName, String comparison, double diffPercent) {
        diffs.computeIfAbsent(sceneName, k -> new ConcurrentHashMap<>())
                .put(comparison, diffPercent);
    }

    public void writeToFile(String outputDir) {
        var sb = new StringBuilder();
        sb.append("{\n");
        var entries = diffs.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            sb.append("  \"").append(entry.getKey()).append("\": {");
            var comparisons = entry.getValue().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList();
            for (int j = 0; j < comparisons.size(); j++) {
                var comp = comparisons.get(j);
                sb.append("\"").append(comp.getKey()).append("\": ")
                  .append(String.format("%.4f", comp.getValue()));
                if (j < comparisons.size() - 1) sb.append(", ");
            }
            sb.append("}");
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("}\n");

        try {
            var dir = new File(outputDir);
            dir.mkdirs();
            Files.writeString(Path.of(outputDir, "diffs.json"), sb.toString());
        } catch (IOException e) {
            System.err.println("Warning: failed to write diffs.json: " + e.getMessage());
        }
    }
}
