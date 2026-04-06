package dev.engine.tests.screenshot.desktop;

import dev.engine.tests.screenshot.SceneDiscovery;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates an HTML report from screenshot test results.
 * Scans build/screenshots/ for images organized by backend, groups by category,
 * and shows pass/fail status from JUnit XML results.
 *
 * <p>Run via: {@code java dev.engine.tests.screenshot.ScreenshotReportGenerator}
 */
public class ScreenshotReportGenerator {

    public static void main(String[] args) throws IOException {
        var screenshotDir = args.length > 0 ? args[0] : "samples/tests/screenshot/build/screenshots";
        var testResultDir = args.length > 1 ? args[1] : "samples/tests/screenshot/build/test-results/test";
        var outputFile = args.length > 2 ? args[2] : "samples/tests/screenshot/build/screenshots/report.html";

        var failures = parseFailures(Path.of(testResultDir));
        var scenes = discoverScreenshots(new File(screenshotDir));
        var diffs = parseDiffs(Path.of(screenshotDir, "diffs.json"));

        var html = generateReport(scenes, failures, diffs, screenshotDir);
        Files.writeString(Path.of(outputFile), html);
        System.out.println("Report: file://" + Path.of(outputFile).toAbsolutePath());
    }

    record SceneResult(String name, String category, Map<String, String> backendImages) {}

    static List<SceneResult> discoverScreenshots(File dir) {
        var scenes = new LinkedHashMap<String, Map<String, String>>();
        var backends = new String[]{"opengl", "vulkan", "webgpu"};

        for (var backend : backends) {
            var backendDir = new File(dir, backend);
            if (!backendDir.exists()) continue;
            var files = backendDir.listFiles((d, name) -> name.endsWith(".png"));
            if (files == null) continue;
            for (var file : files) {
                var sceneName = file.getName().replace(".png", "");
                scenes.computeIfAbsent(sceneName, k -> new LinkedHashMap<>())
                        .put(backend, backend + "/" + file.getName());
            }
        }

        // Derive categories from scene discovery
        var discovery = new SceneDiscovery();
        var categoryMap = new HashMap<String, String>();
        for (var s : discovery.scenes()) categoryMap.put(s.name(), s.category());

        return scenes.entrySet().stream()
                .filter(e -> categoryMap.containsKey(e.getKey())) // only scenes known to discovery
                .map(e -> new SceneResult(e.getKey(),
                        categoryMap.get(e.getKey()),
                        e.getValue()))
                .sorted(Comparator.comparing(SceneResult::category).thenComparing(SceneResult::name))
                .collect(Collectors.toList());
    }

    static Set<String> parseFailures(Path testResultDir) {
        var failures = new HashSet<String>();
        if (!Files.exists(testResultDir)) return failures;
        try (var stream = Files.list(testResultDir)) {
            stream.filter(p -> p.toString().endsWith(".xml")).forEach(p -> {
                try {
                    var content = Files.readString(p);
                    // Simple XML parsing — find failed test names
                    var idx = 0;
                    while ((idx = content.indexOf("<testcase", idx)) >= 0) {
                        var end = content.indexOf("/>", idx);
                        var closeTag = content.indexOf("</testcase>", idx);
                        var blockEnd = Math.min(
                                end >= 0 ? end : Integer.MAX_VALUE,
                                closeTag >= 0 ? closeTag : Integer.MAX_VALUE);
                        if (blockEnd == Integer.MAX_VALUE) break;
                        var block = content.substring(idx, blockEnd);
                        if (block.contains("<failure") || block.contains("<error")) {
                            var nameStart = block.indexOf("name=\"") + 6;
                            var nameEnd = block.indexOf("\"", nameStart);
                            if (nameStart > 5 && nameEnd > nameStart) {
                                failures.add(block.substring(nameStart, nameEnd));
                            }
                        }
                        idx = blockEnd + 1;
                    }
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
        return failures;
    }

    static Map<String, Map<String, Double>> parseDiffs(Path diffsFile) {
        var result = new HashMap<String, Map<String, Double>>();
        if (!Files.exists(diffsFile)) return result;
        try {
            var content = Files.readString(diffsFile);
            // Simple JSON parsing — good enough for our format
            var lines = content.split("\n");
            String currentScene = null;
            for (var line : lines) {
                line = line.trim();
                if (line.startsWith("\"") && line.contains("\": {")) {
                    currentScene = line.substring(1, line.indexOf("\"", 1));
                    var inner = line.substring(line.indexOf("{") + 1, line.lastIndexOf("}"));
                    var pairs = inner.split(",");
                    var map = new HashMap<String, Double>();
                    for (var pair : pairs) {
                        pair = pair.trim();
                        if (pair.isEmpty()) continue;
                        var key = pair.substring(1, pair.indexOf("\"", 1));
                        var val = pair.substring(pair.indexOf(":") + 1).trim();
                        try { map.put(key, Double.parseDouble(val)); } catch (NumberFormatException ignored) {}
                    }
                    result.put(currentScene, map);
                }
            }
        } catch (IOException ignored) {}
        return result;
    }

    static String generateReport(List<SceneResult> scenes, Set<String> failures,
                                  Map<String, Map<String, Double>> diffs, String screenshotDir) {
        var sb = new StringBuilder();
        sb.append("""
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <title>Screenshot Regression Report</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                       background: #1a1a2e; color: #e0e0e0; margin: 0; padding: 20px; }
                h1 { color: #e0e0e0; border-bottom: 2px solid #333; padding-bottom: 10px; }
                h2 { color: #aaa; margin-top: 30px; font-size: 1.1em; text-transform: uppercase;
                     letter-spacing: 1px; border-bottom: 1px solid #333; padding-bottom: 5px; }
                .summary { background: #16213e; border-radius: 8px; padding: 15px; margin: 20px 0;
                           display: flex; gap: 20px; align-items: center; }
                .summary .count { font-size: 2em; font-weight: bold; }
                .summary .pass { color: #4ade80; }
                .summary .fail { color: #f87171; }
                .scene { display: flex; align-items: flex-start; gap: 15px; padding: 12px;
                         margin: 8px 0; background: #16213e; border-radius: 8px; }
                .scene.failed { border-left: 4px solid #f87171; }
                .scene-name { min-width: 200px; font-weight: 600; padding-top: 5px; }
                .images { display: flex; gap: 8px; flex-wrap: wrap; }
                .image-card { text-align: center; }
                .image-card img { width: 128px; height: 128px; object-fit: contain;
                                  border-radius: 4px; border: 1px solid #333; background: #0a0a1a; }
                .image-card .label { font-size: 0.75em; color: #888; margin-top: 2px; }
                .badge { display: inline-block; padding: 2px 8px; border-radius: 4px;
                         font-size: 0.75em; font-weight: 600; margin: 2px; }
                .badge.pass { background: #065f46; color: #4ade80; }
                .badge.fail { background: #7f1d1d; color: #f87171; }
                .badge.skip { background: #333; color: #888; }
                .badges { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; }
                .failures-section { margin-top: 30px; }
                .failures-section h2 { color: #f87171; }
                .failure-detail { background: #1c1017; border-radius: 8px; padding: 15px;
                                  margin: 10px 0; border-left: 4px solid #f87171; }
                .failure-detail img { width: 200px; height: 200px; object-fit: contain;
                                      border-radius: 4px; border: 1px solid #555; }
                .filter-bar { background: #0f3460; border-radius: 8px; padding: 12px 16px;
                              margin: 15px 0; display: flex; gap: 10px; align-items: center;
                              flex-wrap: wrap; position: sticky; top: 0; z-index: 10; }
                .filter-btn { padding: 6px 14px; border-radius: 6px; border: 1px solid #334;
                              background: #16213e; color: #ccc; cursor: pointer; font-size: 0.85em;
                              transition: all 0.15s; }
                .filter-btn:hover { background: #1a2744; border-color: #556; }
                .filter-btn.active { background: #1a56db; color: #fff; border-color: #1a56db; }
                .filter-btn.active-fail { background: #991b1b; color: #fca5a5; border-color: #991b1b; }
                .filter-label { color: #888; font-size: 0.8em; text-transform: uppercase;
                                letter-spacing: 1px; }
                .scene.hidden { display: none; }
                .category.hidden { display: none; }
            </style>
            <script>
            function setFilter(mode) {
                document.querySelectorAll('.filter-btn').forEach(b => {
                    b.classList.remove('active', 'active-fail');
                });
                event.target.classList.add(mode === 'failed' ? 'active-fail' : 'active');

                document.querySelectorAll('.scene').forEach(s => {
                    if (mode === 'all') {
                        s.classList.remove('hidden');
                    } else if (mode === 'failed') {
                        s.classList.toggle('hidden', !s.classList.contains('failed'));
                    } else if (mode === 'passed') {
                        s.classList.toggle('hidden', s.classList.contains('failed'));
                    }
                });
                // Hide empty categories
                document.querySelectorAll('.category').forEach(cat => {
                    var visible = cat.querySelectorAll('.scene:not(.hidden)').length;
                    cat.classList.toggle('hidden', visible === 0);
                });
            }
            </script>
            </head><body>
            <h1>Screenshot Regression Report</h1>
            """);

        // Summary
        int total = scenes.size();
        long failCount = scenes.stream().filter(s -> isSceneFailed(s, failures)).count();
        long passCount = total - failCount;
        sb.append("<div class=\"summary\">");
        sb.append("<div><span class=\"count pass\">").append(passCount).append("</span> passed</div>");
        if (failCount > 0) {
            sb.append("<div><span class=\"count fail\">").append(failCount).append("</span> failed</div>");
        }
        sb.append("<div>").append(total).append(" scenes &times; 3 backends</div>");
        sb.append("</div>");

        // Filter bar
        sb.append("<div class=\"filter-bar\">");
        sb.append("<span class=\"filter-label\">Filter:</span>");
        sb.append("<button class=\"filter-btn active\" onclick=\"setFilter('all')\">All (").append(total).append(")</button>");
        sb.append("<button class=\"filter-btn\" onclick=\"setFilter('passed')\">Passed (").append(passCount).append(")</button>");
        if (failCount > 0) {
            sb.append("<button class=\"filter-btn\" onclick=\"setFilter('failed')\">Failed (").append(failCount).append(")</button>");
        }
        sb.append("</div>");

        // Group by category
        var byCategory = scenes.stream().collect(Collectors.groupingBy(
                SceneResult::category, LinkedHashMap::new, Collectors.toList()));

        for (var entry : byCategory.entrySet()) {
            sb.append("<div class=\"category\">");
            sb.append("<h2>").append(entry.getKey()).append(" (")
              .append(entry.getValue().size()).append(")</h2>");
            for (var scene : entry.getValue()) {
                renderScene(sb, scene, failures, diffs, false);
            }
            sb.append("</div>");
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    static void renderScene(StringBuilder sb, SceneResult scene, Set<String> failures,
                             Map<String, Map<String, Double>> diffs, boolean expanded) {
        boolean failed = isSceneFailed(scene, failures);
        sb.append("<div class=\"scene").append(failed ? " failed" : "").append("\">");
        sb.append("<div class=\"scene-name\">").append(scene.name());

        // Badges — vertical, full names
        sb.append("<div class=\"badges\">");
        var backendNames = Map.of("opengl", "OpenGL", "vulkan", "Vulkan", "webgpu", "WebGPU");
        var sceneDiffs = diffs.getOrDefault(scene.name, Map.of());
        for (var backend : new String[]{"opengl", "vulkan", "webgpu"}) {
            var label = backendNames.get(backend);
            boolean hasImage = scene.backendImages.containsKey(backend);
            boolean backendFailed = failures.contains(scene.name + "[" + backend + "]")
                    || failures.contains(backend + " > " + scene.name);
            var refDiff = sceneDiffs.get(backend + "_ref");
            var diffStr = refDiff != null ? String.format(" %.2f%%", refDiff) : "";
            if (!hasImage) {
                sb.append("<span class=\"badge skip\">").append(label).append(" skip</span>");
            } else if (backendFailed) {
                sb.append("<span class=\"badge fail\">").append(label).append(" ✗").append(diffStr).append("</span>");
            } else {
                sb.append("<span class=\"badge pass\">").append(label).append(" ✓").append(diffStr).append("</span>");
            }
        }
        // Cross-backend badges with diff values
        boolean crossFailed = failures.stream().anyMatch(f ->
                f.contains(scene.name) && (f.contains("cross") || f.contains("Cross")));
        if (scene.backendImages.size() >= 2) {
            var glVkDiff = sceneDiffs.get("gl_vs_vk");
            var glWgpuDiff = sceneDiffs.get("gl_vs_webgpu");
            if (glVkDiff != null) {
                var badge = crossFailed ? "fail" : "pass";
                var icon = crossFailed ? "✗" : "✓";
                sb.append("<span class=\"badge ").append(badge).append("\">GL↔VK ")
                  .append(icon).append(String.format(" %.2f%%", glVkDiff)).append("</span>");
            }
            if (glWgpuDiff != null) {
                sb.append("<span class=\"badge pass\">GL↔WebGPU ")
                  .append(String.format("%.2f%%", glWgpuDiff)).append("</span>");
            }
        }
        sb.append("</div></div>");

        // Images
        sb.append("<div class=\"images\">");
        for (var entry : scene.backendImages.entrySet()) {
            sb.append("<div class=\"image-card\">");
            var imgSize = expanded ? "200" : "128";
            sb.append("<img src=\"").append(entry.getValue()).append("\" width=\"").append(imgSize)
              .append("\" height=\"").append(imgSize).append("\">");
            sb.append("<div class=\"label\">").append(backendNames.getOrDefault(entry.getKey(), entry.getKey())).append("</div>");
            sb.append("</div>");
        }
        sb.append("</div></div>");
    }

    static boolean isSceneFailed(SceneResult scene, Set<String> failures) {
        for (var f : failures) {
            if (f.contains(scene.name)) return true;
        }
        return false;
    }
}
