package dev.engine.tests.screenshot;

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

        var html = generateReport(scenes, failures, screenshotDir);
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
                .map(e -> new SceneResult(e.getKey(),
                        categoryMap.getOrDefault(e.getKey(), "Other"),
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

    static String generateReport(List<SceneResult> scenes, Set<String> failures, String screenshotDir) {
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
                .badges { display: flex; gap: 4px; flex-wrap: wrap; margin-top: 4px; }
                .failures-section { margin-top: 30px; }
                .failures-section h2 { color: #f87171; }
                .failure-detail { background: #1c1017; border-radius: 8px; padding: 15px;
                                  margin: 10px 0; border-left: 4px solid #f87171; }
                .failure-detail img { width: 200px; height: 200px; object-fit: contain;
                                      border-radius: 4px; border: 1px solid #555; }
            </style>
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

        // Failures first
        var failedScenes = scenes.stream().filter(s -> isSceneFailed(s, failures)).toList();
        if (!failedScenes.isEmpty()) {
            sb.append("<div class=\"failures-section\"><h2>Failures</h2>");
            for (var scene : failedScenes) {
                renderScene(sb, scene, failures, true);
            }
            sb.append("</div>");
        }

        // Group by category
        var byCategory = scenes.stream().collect(Collectors.groupingBy(
                SceneResult::category, LinkedHashMap::new, Collectors.toList()));

        for (var entry : byCategory.entrySet()) {
            sb.append("<h2>").append(entry.getKey()).append(" (")
              .append(entry.getValue().size()).append(")</h2>");
            for (var scene : entry.getValue()) {
                renderScene(sb, scene, failures, false);
            }
        }

        sb.append("</body></html>");
        return sb.toString();
    }

    static void renderScene(StringBuilder sb, SceneResult scene, Set<String> failures, boolean expanded) {
        boolean failed = isSceneFailed(scene, failures);
        sb.append("<div class=\"scene").append(failed ? " failed" : "").append("\">");
        sb.append("<div class=\"scene-name\">").append(scene.name());

        // Badges
        sb.append("<div class=\"badges\">");
        for (var backend : new String[]{"opengl", "vulkan", "webgpu"}) {
            boolean hasImage = scene.backendImages.containsKey(backend);
            boolean backendFailed = failures.contains(scene.name + "[" + backend + "]")
                    || failures.contains(backend + " > " + scene.name);
            if (!hasImage) {
                sb.append("<span class=\"badge skip\">").append(backend.toUpperCase().charAt(0)).append("L skip</span>");
            } else if (backendFailed) {
                sb.append("<span class=\"badge fail\">").append(backend.substring(0, 2).toUpperCase()).append(" ✗</span>");
            } else {
                sb.append("<span class=\"badge pass\">").append(backend.substring(0, 2).toUpperCase()).append(" ✓</span>");
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
            sb.append("<div class=\"label\">").append(entry.getKey()).append("</div>");
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
