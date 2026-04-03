package dev.engine.examples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Generates an HTML report combining JUnit test results with screenshot comparisons.
 * Parses JUnit XML reports and matches them to screenshot images.
 *
 * Run via: ./gradlew :examples:screenshotReport
 */
public final class ScreenshotReportGenerator {

    private ScreenshotReportGenerator() {}

    record TestResult(String className, String testName, String sceneName, Status status, String message, double durationSec) {
        enum Status { PASSED, FAILED, SKIPPED }
    }

    public static void main(String[] args) throws Exception {
        var screenshotDir = Path.of(args.length > 0 ? args[0] : "examples/build/screenshots");
        var junitDir = Path.of(args.length > 1 ? args[1] : "examples/build/test-results/test");
        var outputFile = Path.of(args.length > 2 ? args[2] : "examples/build/screenshots/report.html");

        // Parse JUnit XML results
        var results = parseJunitResults(junitDir);

        // Collect screenshot files
        var glDir = screenshotDir.resolve("opengl");
        var vkDir = screenshotDir.resolve("vulkan");
        var scenes = new TreeSet<String>();
        collectSceneNames(glDir, scenes);
        collectSceneNames(vkDir, scenes);

        // Also add scenes from test results that might not have screenshots
        for (var r : results) {
            if (r.sceneName != null) scenes.add(r.sceneName);
        }

        var html = generateReport(scenes, results, glDir, vkDir);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, html);
        System.out.println("Screenshot report generated: " + outputFile.toAbsolutePath());
    }

    private static List<TestResult> parseJunitResults(Path junitDir) throws Exception {
        var results = new ArrayList<TestResult>();
        if (!Files.exists(junitDir)) return results;

        try (var stream = Files.list(junitDir)) {
            var xmlFiles = stream.filter(p -> p.toString().endsWith(".xml")).toList();
            var factory = DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();

            for (var xmlFile : xmlFiles) {
                var doc = builder.parse(xmlFile.toFile());
                var testcases = doc.getElementsByTagName("testcase");
                for (int i = 0; i < testcases.getLength(); i++) {
                    var tc = testcases.item(i);
                    var attrs = tc.getAttributes();
                    var className = attrs.getNamedItem("classname").getTextContent();
                    var testName = attrs.getNamedItem("name").getTextContent();
                    var timeStr = attrs.getNamedItem("time");
                    double duration = timeStr != null ? Double.parseDouble(timeStr.getTextContent()) : 0;

                    // Check for failure/error/skipped children
                    TestResult.Status status = TestResult.Status.PASSED;
                    String message = null;
                    var children = tc.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        var child = children.item(j);
                        if ("failure".equals(child.getNodeName()) || "error".equals(child.getNodeName())) {
                            status = TestResult.Status.FAILED;
                            var msgAttr = child.getAttributes().getNamedItem("message");
                            message = msgAttr != null ? msgAttr.getTextContent() : child.getTextContent();
                            if (message != null && message.length() > 300) {
                                message = message.substring(0, 300) + "...";
                            }
                        } else if ("skipped".equals(child.getNodeName())) {
                            status = TestResult.Status.SKIPPED;
                        }
                    }

                    // Derive scene name from test method name (e.g., "twoCubesUnlit" -> "two_cubes_unlit")
                    String sceneName = camelToSnake(testName);

                    results.add(new TestResult(className, testName, sceneName, status, message, duration));
                }
            }
        }
        return results;
    }

    private static String camelToSnake(String camel) {
        // Strip JUnit method signature suffix like "()" before converting
        var clean = camel.replaceAll("\\(.*\\)", "");
        return clean.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private static void collectSceneNames(Path dir, Set<String> names) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".png"))
                  .map(p -> p.getFileName().toString().replace(".png", ""))
                  .forEach(names::add);
        }
    }

    private static String generateReport(Set<String> scenes, List<TestResult> results, Path glDir, Path vkDir) {
        // Index results by scene name
        var glResults = new HashMap<String, TestResult>();
        var vkResults = new HashMap<String, TestResult>();
        var crossResults = new HashMap<String, TestResult>();
        int totalPassed = 0, totalFailed = 0, totalSkipped = 0;

        for (var r : results) {
            if (r.className.contains("OpenGl")) glResults.put(r.sceneName, r);
            else if (r.className.contains("Vulkan")) vkResults.put(r.sceneName, r);
            else if (r.className.contains("CrossBackend")) crossResults.put(r.sceneName, r);

            switch (r.status) {
                case PASSED -> totalPassed++;
                case FAILED -> totalFailed++;
                case SKIPPED -> totalSkipped++;
            }
        }

        var rows = new StringBuilder();
        for (var scene : scenes) {
            var glFile = glDir.resolve(scene + ".png");
            var vkFile = vkDir.resolve(scene + ".png");
            boolean hasGl = Files.exists(glFile);
            boolean hasVk = Files.exists(vkFile);

            var glResult = glResults.get(scene);
            var vkResult = vkResults.get(scene);
            var crossResult = crossResults.get(scene);

            String displayName = scene.replace("_", " ");
            String glImg = hasGl ? "<img src=\"opengl/" + scene + ".png\" alt=\"OpenGL\">" : "<span class=\"missing\">no image</span>";
            String vkImg = hasVk ? "<img src=\"vulkan/" + scene + ".png\" alt=\"Vulkan\">" : "<span class=\"missing\">no image</span>";

            String glStatus = statusBadge(glResult);
            String vkStatus = statusBadge(vkResult);
            String crossStatus = statusBadge(crossResult);

            String failMessage = "";
            if (glResult != null && glResult.status == TestResult.Status.FAILED) {
                failMessage += "<div class=\"fail-msg\">GL: " + escapeHtml(glResult.message) + "</div>";
            }
            if (vkResult != null && vkResult.status == TestResult.Status.FAILED) {
                failMessage += "<div class=\"fail-msg\">VK: " + escapeHtml(vkResult.message) + "</div>";
            }
            if (crossResult != null && crossResult.status == TestResult.Status.FAILED) {
                failMessage += "<div class=\"fail-msg\">Cross: " + escapeHtml(crossResult.message) + "</div>";
            }

            boolean anyFailed = (glResult != null && glResult.status == TestResult.Status.FAILED)
                || (vkResult != null && vkResult.status == TestResult.Status.FAILED)
                || (crossResult != null && crossResult.status == TestResult.Status.FAILED);

            rows.append("""
                <tr class="%s">
                    <td class="scene-name">
                        %s
                        <div class="badges">%s %s %s</div>
                        %s
                    </td>
                    <td class="screenshot">%s</td>
                    <td class="screenshot">%s</td>
                </tr>
            """.formatted(
                anyFailed ? "failed-row" : "",
                displayName, glStatus, vkStatus, crossStatus,
                failMessage,
                glImg, vkImg
            ));
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Screenshot Test Report</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: #0d1117;
                        color: #c9d1d9;
                        padding: 2rem;
                    }
                    h1 { color: #f0f6fc; margin-bottom: 0.5rem; font-size: 1.8rem; }
                    .subtitle { color: #8b949e; margin-bottom: 2rem; font-size: 0.9rem; }
                    .stats { display: flex; gap: 1.5rem; margin-bottom: 2rem; flex-wrap: wrap; }
                    .stat {
                        background: #161b22; border: 1px solid #30363d;
                        border-radius: 8px; padding: 1rem 1.5rem;
                    }
                    .stat-value { font-size: 2rem; font-weight: bold; }
                    .stat-value.green { color: #3fb950; }
                    .stat-value.red { color: #f85149; }
                    .stat-value.yellow { color: #d29922; }
                    .stat-value.blue { color: #58a6ff; }
                    .stat-label { color: #8b949e; font-size: 0.85rem; }
                    table {
                        width: 100%%; border-collapse: collapse;
                        background: #161b22; border: 1px solid #30363d;
                        border-radius: 8px; overflow: hidden;
                    }
                    th {
                        background: #21262d; color: #f0f6fc; padding: 1rem;
                        text-align: left; font-weight: 600; border-bottom: 1px solid #30363d;
                    }
                    td {
                        padding: 0.75rem 1rem; border-bottom: 1px solid #21262d; vertical-align: top;
                    }
                    .scene-name {
                        font-weight: 500; text-transform: capitalize;
                        min-width: 200px; color: #f0f6fc;
                    }
                    .badges { margin-top: 0.4rem; display: flex; gap: 0.3rem; flex-wrap: wrap; }
                    .badge {
                        font-size: 0.7rem; padding: 0.15rem 0.5rem;
                        border-radius: 10px; font-weight: 500;
                    }
                    .badge.pass { background: #238636; color: #fff; }
                    .badge.fail { background: #da3633; color: #fff; }
                    .badge.skip { background: #6e7681; color: #fff; }
                    .badge.none { background: #30363d; color: #8b949e; }
                    .fail-msg {
                        margin-top: 0.4rem; font-size: 0.75rem; color: #f85149;
                        background: #21262d; padding: 0.4rem 0.6rem;
                        border-radius: 4px; border-left: 3px solid #da3633;
                        word-break: break-word; max-width: 300px;
                    }
                    .screenshot { text-align: center; }
                    .screenshot img {
                        width: 256px; height: 256px; image-rendering: pixelated;
                        border: 2px solid #30363d; border-radius: 4px;
                        background: #0d1117; transition: transform 0.2s; cursor: pointer;
                    }
                    .screenshot img:hover {
                        transform: scale(2); z-index: 10; position: relative; border-color: #58a6ff;
                    }
                    .missing { color: #8b949e; font-style: italic; font-size: 0.85rem; }
                    tr:hover { background: #1c2128; }
                    .failed-row { background: #1c1012; }
                    .failed-row:hover { background: #261418; }
                    .timestamp { color: #484f58; font-size: 0.8rem; margin-top: 2rem; }
                </style>
            </head>
            <body>
                <h1>Screenshot Test Report</h1>
                <p class="subtitle">Visual regression test results &mdash; OpenGL 4.5 vs Vulkan 1.3</p>

                <div class="stats">
                    <div class="stat">
                        <div class="stat-value blue">%d</div>
                        <div class="stat-label">Test Scenes</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value green">%d</div>
                        <div class="stat-label">Passed</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value red">%d</div>
                        <div class="stat-label">Failed</div>
                    </div>
                    <div class="stat">
                        <div class="stat-value yellow">%d</div>
                        <div class="stat-label">Skipped</div>
                    </div>
                </div>

                <table>
                    <thead>
                        <tr>
                            <th>Scene / Status</th>
                            <th>OpenGL</th>
                            <th>Vulkan</th>
                        </tr>
                    </thead>
                    <tbody>
                        %s
                    </tbody>
                </table>

                <p class="timestamp">Generated: %s</p>
            </body>
            </html>
            """.formatted(
                scenes.size(), totalPassed, totalFailed, totalSkipped,
                rows.toString(),
                java.time.LocalDateTime.now().toString()
            );
    }

    private static String statusBadge(TestResult result) {
        if (result == null) return "<span class=\"badge none\">n/a</span>";
        return switch (result.status) {
            case PASSED -> "<span class=\"badge pass\">%s pass</span>".formatted(badgeLabel(result));
            case FAILED -> "<span class=\"badge fail\">%s fail</span>".formatted(badgeLabel(result));
            case SKIPPED -> "<span class=\"badge skip\">%s skip</span>".formatted(badgeLabel(result));
        };
    }

    private static String badgeLabel(TestResult result) {
        if (result.className.contains("OpenGl")) return "GL";
        if (result.className.contains("Vulkan")) return "VK";
        if (result.className.contains("CrossBackend")) return "Cross";
        return "?";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
