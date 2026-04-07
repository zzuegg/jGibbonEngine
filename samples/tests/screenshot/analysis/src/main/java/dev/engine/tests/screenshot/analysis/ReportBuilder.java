package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline Pass 4: Generates an interactive HTML report from the manifest.
 */
public final class ReportBuilder {

    private ReportBuilder() {}

    public static void generate(Path manifestPath, Path screenshotDir, Path outputFile)
            throws Exception {
        var manifest = Manifest.readFrom(manifestPath);
        var html = buildHtml(manifest);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, html);
    }

    static String buildHtml(Manifest manifest) {
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
                .meta { background: #16213e; border-radius: 8px; padding: 12px 16px; margin: 10px 0;
                        font-size: 0.85em; color: #888; display: flex; gap: 20px; flex-wrap: wrap; }
                .meta span { white-space: nowrap; }
                .summary { background: #16213e; border-radius: 8px; padding: 15px; margin: 20px 0;
                           display: flex; gap: 20px; align-items: center; }
                .summary .count { font-size: 2em; font-weight: bold; }
                .summary .pass { color: #4ade80; }
                .summary .fail { color: #f87171; }
                .summary .skip { color: #888; }
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
                .badge.noref { background: #422006; color: #fb923c; }
                .badges { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; }
                .error-details { background: #1c1017; border-radius: 6px; padding: 10px;
                                 margin-top: 8px; font-size: 0.8em; max-height: 200px;
                                 overflow-y: auto; white-space: pre-wrap; font-family: monospace; }
                .filter-bar { background: #0f3460; border-radius: 8px; padding: 12px 16px;
                              margin: 15px 0; display: flex; gap: 10px; align-items: center;
                              flex-wrap: wrap; position: sticky; top: 0; z-index: 10; }
                .filter-btn { padding: 6px 14px; border-radius: 6px; border: 1px solid #334;
                              background: #16213e; color: #ccc; cursor: pointer; font-size: 0.85em; }
                .filter-btn:hover { background: #1a2744; }
                .filter-btn.active { background: #1a56db; color: #fff; border-color: #1a56db; }
                .filter-btn.active-fail { background: #991b1b; color: #fca5a5; border-color: #991b1b; }
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
                    if (mode === 'all') s.classList.remove('hidden');
                    else if (mode === 'failed') s.classList.toggle('hidden', !s.classList.contains('failed'));
                    else if (mode === 'passed') s.classList.toggle('hidden', s.classList.contains('failed'));
                });
                document.querySelectorAll('.category').forEach(cat => {
                    var visible = cat.querySelectorAll('.scene:not(.hidden)').length;
                    cat.classList.toggle('hidden', visible === 0);
                });
            }
            </script>
            </head><body>
            """);

        sb.append("<h1>Screenshot Regression Report</h1>\n");

        // Metadata
        sb.append("<div class=\"meta\">");
        sb.append("<span>Branch: <b>").append(esc(manifest.branch)).append("</b></span>");
        sb.append("<span>Commit: <b>").append(esc(manifest.commit)).append("</b></span>");
        sb.append("<span>Profile: <b>").append(esc(manifest.profile)).append("</b></span>");
        sb.append("<span>").append(esc(manifest.timestamp)).append("</span>");
        sb.append("</div>\n");

        // Collect scene data
        var sceneNames = manifest.scenes.stream().map(s -> s.name).toList();
        var sceneCategories = manifest.scenes.stream()
                .collect(Collectors.toMap(s -> s.name, s -> s.category, (a, b) -> a));

        // Count pass/fail
        long failCount = sceneNames.stream().filter(name ->
                manifest.comparisons.stream().anyMatch(c ->
                        c.scene.equals(name) && "fail".equals(c.status))).count();
        long passCount = sceneNames.size() - failCount;

        // Summary
        sb.append("<div class=\"summary\">");
        sb.append("<div><span class=\"count pass\">").append(passCount).append("</span> passed</div>");
        if (failCount > 0) {
            sb.append("<div><span class=\"count fail\">").append(failCount).append("</span> failed</div>");
        }
        sb.append("<div>").append(sceneNames.size()).append(" scenes</div>");
        sb.append("</div>\n");

        // Filter bar
        sb.append("<div class=\"filter-bar\">");
        sb.append("<span style=\"color:#888;font-size:0.8em;text-transform:uppercase;letter-spacing:1px\">Filter:</span>");
        sb.append("<button class=\"filter-btn active\" onclick=\"setFilter('all')\">All (").append(sceneNames.size()).append(")</button>");
        sb.append("<button class=\"filter-btn\" onclick=\"setFilter('passed')\">Passed (").append(passCount).append(")</button>");
        if (failCount > 0) {
            sb.append("<button class=\"filter-btn\" onclick=\"setFilter('failed')\">Failed (").append(failCount).append(")</button>");
        }
        sb.append("</div>\n");

        // Group scenes by category
        var byCategory = new LinkedHashMap<String, List<String>>();
        for (var scene : manifest.scenes) {
            byCategory.computeIfAbsent(scene.category, k -> new ArrayList<>()).add(scene.name);
        }

        for (var entry : byCategory.entrySet()) {
            sb.append("<div class=\"category\">");
            sb.append("<h2>").append(esc(entry.getKey())).append(" (")
              .append(entry.getValue().size()).append(")</h2>\n");

            for (var sceneName : entry.getValue()) {
                boolean failed = manifest.comparisons.stream()
                        .anyMatch(c -> c.scene.equals(sceneName) && "fail".equals(c.status));
                sb.append("<div class=\"scene").append(failed ? " failed" : "").append("\">");
                sb.append("<div class=\"scene-name\">").append(esc(sceneName));

                // Badges
                sb.append("<div class=\"badges\">");

                // Reference comparison badges
                var refComps = manifest.comparisons.stream()
                        .filter(c -> c.scene.equals(sceneName) && "reference".equals(c.type))
                        .toList();
                for (var comp : refComps) {
                    var badgeClass = switch (comp.status) {
                        case "pass" -> "pass";
                        case "fail" -> "fail";
                        case "no_reference" -> "noref";
                        default -> "skip";
                    };
                    var icon = switch (comp.status) {
                        case "pass" -> " ✓";
                        case "fail" -> " ✗";
                        default -> "";
                    };
                    var diffStr = comp.diffPercent > 0
                            ? String.format(" %.2f%%", comp.diffPercent) : "";
                    sb.append("<span class=\"badge ").append(badgeClass).append("\">")
                      .append(esc(comp.backend != null ? comp.backend : "?"))
                      .append(icon).append(diffStr).append("</span>");
                }

                // Cross-backend badges
                var crossComps = manifest.comparisons.stream()
                        .filter(c -> c.scene.equals(sceneName) && "cross_backend".equals(c.type))
                        .toList();
                for (var comp : crossComps) {
                    var badgeClass = "fail".equals(comp.status) ? "fail" : "pass";
                    var icon = "fail".equals(comp.status) ? "✗" : "✓";
                    sb.append("<span class=\"badge ").append(badgeClass).append("\">")
                      .append(esc(comp.backendA)).append("↔").append(esc(comp.backendB))
                      .append(" ").append(icon)
                      .append(String.format(" %.2f%%", comp.diffPercent)).append("</span>");
                }

                sb.append("</div></div>\n"); // badges + scene-name

                // Images
                sb.append("<div class=\"images\">");
                var runs = manifest.runs.stream()
                        .filter(r -> r.scene.equals(sceneName) && "success".equals(r.status))
                        .toList();
                for (var run : runs) {
                    for (var screenshot : run.screenshots) {
                        sb.append("<div class=\"image-card\">");
                        sb.append("<img src=\"").append(esc(screenshot.path())).append("\" width=\"128\" height=\"128\">");
                        sb.append("<div class=\"label\">").append(esc(run.backend)).append("</div>");
                        sb.append("</div>");
                    }
                }

                // Error details for failed runs
                var failedRuns = manifest.runs.stream()
                        .filter(r -> r.scene.equals(sceneName) && !"success".equals(r.status))
                        .toList();
                for (var run : failedRuns) {
                    sb.append("<div class=\"error-details\">");
                    sb.append(esc(run.backend)).append(" [").append(esc(run.status)).append("]");
                    if (run.error != null) {
                        sb.append("\n").append(esc(run.error.message()));
                        if (run.error.stderr() != null && !run.error.stderr().isEmpty()) {
                            sb.append("\n--- stderr ---\n").append(esc(run.error.stderr()));
                        }
                    }
                    sb.append("</div>");
                }

                sb.append("</div></div>\n"); // images + scene
            }
            sb.append("</div>\n"); // category
        }

        sb.append("</body></html>\n");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /** Entry point for Gradle JavaExec task. Args: manifestPath screenshotDir outputFile */
    public static void main(String[] args) throws Exception {
        generate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("Report: file://" + Path.of(args[2]).toAbsolutePath());
    }
}
