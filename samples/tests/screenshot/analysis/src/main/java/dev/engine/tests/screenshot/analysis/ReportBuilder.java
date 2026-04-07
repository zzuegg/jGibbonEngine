package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline Pass 4: Generates an interactive HTML report from the manifest,
 * styled to match the jGibbonEngine website design system.
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

        // Group scenes by category
        var byCategory = new LinkedHashMap<String, List<Manifest.Scene>>();
        for (var scene : manifest.scenes) {
            byCategory.computeIfAbsent(scene.category, k -> new ArrayList<>()).add(scene);
        }

        // Compute per-scene status: "failed", "partial", "passed"
        var sceneStatuses = new LinkedHashMap<String, String>();
        for (var scene : manifest.scenes) {
            boolean hasFail = manifest.comparisons.stream()
                    .anyMatch(c -> c.scene.equals(scene.name) && "fail".equals(c.status));
            boolean hasKnown = manifest.comparisons.stream()
                    .anyMatch(c -> c.scene.equals(scene.name) && "known_limitation".equals(c.status));
            boolean hasError = manifest.runs.stream()
                    .anyMatch(r -> r.scene.equals(scene.name) && !"success".equals(r.status));
            if (hasFail) sceneStatuses.put(scene.name, "failed");
            else if (hasKnown || hasError) sceneStatuses.put(scene.name, "partial");
            else sceneStatuses.put(scene.name, "passed");
        }

        // Count stats
        long totalScenes = manifest.scenes.size();
        long failCount = sceneStatuses.values().stream().filter("failed"::equals).count();
        long partialCount = sceneStatuses.values().stream().filter("partial"::equals).count();
        long passCount = sceneStatuses.values().stream().filter("passed"::equals).count();
        long totalRuns = manifest.runs.size();
        long crashes = manifest.runs.stream().filter(r -> "crash".equals(r.status)).count();

        // Collect all backends that actually ran
        var allBackends = manifest.runs.stream()
                .map(r -> r.backend).distinct().sorted().toList();

        sb.append("""
            <!DOCTYPE html>
            <html lang="en"><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Screenshot Report &mdash; jGibbonEngine</title>
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
            <link href="https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@400;500;600;700&family=Inter:wght@400;500;600&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
            <style>
            """);
        appendCss(sb);
        sb.append("</style>\n");
        appendScript(sb);
        sb.append("</head><body>\n");

        // ── Header ──────────────────────────────────────────────────────
        sb.append("<header class=\"page-header\">\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <div class=\"header-content\">\n");
        sb.append("      <h1>Screenshot Regression Report</h1>\n");
        sb.append("      <div class=\"meta\">\n");
        sb.append("        <span class=\"meta-item\"><span class=\"meta-label\">Branch</span> ")
          .append(esc(manifest.branch)).append("</span>\n");
        sb.append("        <span class=\"meta-item\"><span class=\"meta-label\">Commit</span> ")
          .append(esc(manifest.commit)).append("</span>\n");
        sb.append("        <span class=\"meta-item\"><span class=\"meta-label\">Profile</span> ")
          .append(esc(manifest.profile)).append("</span>\n");
        sb.append("        <span class=\"meta-item\"><span class=\"meta-label\">Time</span> ")
          .append(esc(manifest.timestamp.length() > 19 ? manifest.timestamp.substring(0, 19) : manifest.timestamp))
          .append("</span>\n");
        sb.append("      </div>\n");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</header>\n\n");

        // ── Stats bar ───────────────────────────────────────────────────
        sb.append("<section class=\"stats-section\">\n");
        sb.append("  <div class=\"container\">\n");
        sb.append("    <div class=\"stats-grid\">\n");
        appendStat(sb, String.valueOf(totalScenes), "Scenes");
        appendStat(sb, String.valueOf(passCount), "Passed", "pass");
        if (partialCount > 0) appendStat(sb, String.valueOf(partialCount), "Partial", "partial");
        if (failCount > 0) appendStat(sb, String.valueOf(failCount), "Failed", "fail");
        appendStat(sb, String.valueOf(totalRuns), "Runs");
        if (crashes > 0) appendStat(sb, String.valueOf(crashes), "Crashes", "fail");
        sb.append("    </div>\n");
        sb.append("  </div>\n");
        sb.append("</section>\n\n");

        // ── Filter bar ──────────────────────────────────────────────────
        sb.append("<div class=\"container\">\n");
        sb.append("  <div class=\"filter-bar\">\n");
        sb.append("    <span class=\"filter-label\">Filter</span>\n");
        sb.append("    <button class=\"filter-btn active\" onclick=\"setFilter('all',this)\">All</button>\n");
        sb.append("    <button class=\"filter-btn\" onclick=\"setFilter('passed',this)\">Passed</button>\n");
        if (partialCount > 0) {
            sb.append("    <button class=\"filter-btn\" onclick=\"setFilter('partial',this)\">Partial</button>\n");
        }
        if (failCount > 0) {
            sb.append("    <button class=\"filter-btn\" onclick=\"setFilter('failed',this)\">Failed</button>\n");
        }
        sb.append("  </div>\n");
        sb.append("</div>\n\n");

        // ── Categories & Cards ──────────────────────────────────────────
        for (var entry : byCategory.entrySet()) {
            sb.append("<section class=\"category\" data-category=\"").append(esc(entry.getKey())).append("\">\n");
            sb.append("  <div class=\"container\">\n");
            sb.append("    <div class=\"section-header\">\n");
            sb.append("      <span class=\"section-label\">").append(esc(entry.getKey())).append("</span>\n");
            sb.append("      <h2 class=\"section-title\">").append(esc(entry.getKey()))
              .append(" <span class=\"count\">(").append(entry.getValue().size()).append(")</span></h2>\n");
            sb.append("    </div>\n");

            sb.append("    <div class=\"card-grid\">\n");
            for (var scene : entry.getValue()) {
                appendCard(sb, scene, manifest, allBackends, sceneStatuses);
            }
            sb.append("    </div>\n");
            sb.append("  </div>\n");
            sb.append("</section>\n\n");
        }

        // ── Hidden detail templates ──────────────────────────────────────
        appendDetailTemplates(sb, manifest, allBackends);

        // ── Detail Modal ────────────────────────────────────────────────
        sb.append("""
            <div id="detail-overlay" class="overlay hidden" onclick="if(event.target===this)closeDetail()">
              <div class="detail-panel">
                <button class="detail-close" onclick="closeDetail()">&times;</button>
                <div id="detail-content"></div>
              </div>
            </div>
            """);

        sb.append("</body></html>\n");
        return sb.toString();
    }

    private static void appendCard(StringBuilder sb, Manifest.Scene scene,
                                    Manifest manifest, List<String> allBackends,
                                    Map<String, String> sceneStatuses) {
        var status = sceneStatuses.getOrDefault(scene.name, "passed");
        var statusClass = switch (status) {
            case "failed" -> "card-fail";
            case "partial" -> "card-partial";
            default -> "card-pass";
        };

        // Find first available screenshot for thumbnail (prefer opengl)
        String thumbnail = null;
        for (var backend : List.of("opengl", "vulkan", "webgpu")) {
            var run = manifest.runs.stream()
                    .filter(r -> r.scene.equals(scene.name) && r.backend.equals(backend)
                            && "success".equals(r.status) && !r.screenshots.isEmpty())
                    .findFirst().orElse(null);
            if (run != null) { thumbnail = run.screenshots.get(0).path(); break; }
        }

        sb.append("      <div class=\"card ").append(statusClass)
          .append("\" data-status=\"").append(status)
          .append("\" onclick=\"showDetail('")
          .append(esc(scene.name)).append("')\">\n");

        // Thumbnail
        sb.append("        <div class=\"card-thumbnail\">\n");
        if (thumbnail != null) {
            sb.append("          <img src=\"").append(esc(thumbnail)).append("\" alt=\"").append(esc(scene.name)).append("\">\n");
        } else {
            sb.append("          <div class=\"card-no-image\">No screenshot</div>\n");
        }
        sb.append("        </div>\n");

        // Card body
        sb.append("        <div class=\"card-body\">\n");
        sb.append("          <h3 class=\"card-title\">").append(formatSceneName(scene.name)).append("</h3>\n");

        // Backend badges (based on reference comparison status)
        sb.append("          <div class=\"backend-badges\">\n");
        for (var backend : allBackends) {
            var run = manifest.runs.stream()
                    .filter(r -> r.scene.equals(scene.name) && r.backend.equals(backend))
                    .findFirst().orElse(null);
            var refComp = manifest.comparisons.stream()
                    .filter(c -> c.scene.equals(scene.name) && "reference".equals(c.type)
                            && backend.equals(c.backend))
                    .findFirst().orElse(null);

            // Determine badge style from reference comparison, falling back to run status
            String badgeClass;
            var tooltip = new StringBuilder(backend);
            if (run == null) {
                badgeClass = "badge-skip";
                tooltip.append(" — not run");
            } else if (!"success".equals(run.status)) {
                badgeClass = "badge-error";
                tooltip.append(" — ").append(run.status);
                if (run.error != null && run.error.message() != null) {
                    var msg = run.error.message();
                    tooltip.append(": ").append(msg.length() > 120 ? msg.substring(0, 120) + "..." : msg);
                }
            } else if (refComp == null || "no_reference".equals(refComp.status)) {
                badgeClass = "badge-noref";
                tooltip.append(" — no reference image");
            } else if ("pass".equals(refComp.status)) {
                badgeClass = "badge-pass";
                tooltip.append(" — pass");
                if (refComp.diffPercent > 0) tooltip.append(String.format(" (%.4f%%)", refComp.diffPercent));
            } else {
                badgeClass = "badge-fail";
                tooltip.append(" — fail");
                if (refComp.reason != null) tooltip.append(": ").append(refComp.reason);
            }

            sb.append("            <span class=\"backend-badge ").append(badgeClass)
              .append("\" title=\"").append(esc(tooltip.toString()))
              .append("\">").append(backendShort(backend)).append("</span>\n");
        }
        sb.append("          </div>\n");

        // Compact comparison matrix
        var crossComps = manifest.comparisons.stream()
                .filter(c -> c.scene.equals(scene.name) && "cross_backend".equals(c.type))
                .toList();
        if (!crossComps.isEmpty()) {
            sb.append("          <div class=\"card-matrix\">\n");
            for (var comp : crossComps) {
                var cls = "fail".equals(comp.status) ? "mx-fail"
                        : "known_limitation".equals(comp.status) ? "mx-known" : "mx-pass";
                var mxTooltip = comp.backendA + " vs " + comp.backendB + ": "
                        + String.format("%.2f%%", comp.diffPercent);
                if (comp.reason != null) mxTooltip += " — " + comp.reason;
                sb.append("            <span class=\"mx ").append(cls).append("\" title=\"")
                  .append(esc(mxTooltip))
                  .append("\">").append(backendShort(comp.backendA)).append("↔")
                  .append(backendShort(comp.backendB)).append("</span>\n");
            }
            sb.append("          </div>\n");
        }

        sb.append("        </div>\n"); // card-body
        sb.append("      </div>\n"); // card
    }

    private static void appendStat(StringBuilder sb, String value, String label) {
        appendStat(sb, value, label, null);
    }

    private static void appendStat(StringBuilder sb, String value, String label, String colorClass) {
        sb.append("      <div class=\"stat\">\n");
        sb.append("        <div class=\"stat-value");
        if (colorClass != null) sb.append(" ").append(colorClass);
        sb.append("\">").append(value).append("</div>\n");
        sb.append("        <div class=\"stat-label\">").append(label).append("</div>\n");
        sb.append("      </div>\n");
    }

    private static void appendCss(StringBuilder sb) {
        sb.append("""
            :root {
              --bg-primary: #0d1117;
              --bg-surface: #161b22;
              --bg-elevated: #1c2128;
              --border: #30363d;
              --border-subtle: #21262d;
              --orange: #f97316;
              --orange-light: #fb923c;
              --orange-dim: rgba(249, 115, 22, 0.15);
              --amber: #fbbf24;
              --green: #22c55e;
              --green-dim: rgba(34, 197, 94, 0.15);
              --red: #f87171;
              --red-dim: rgba(248, 113, 113, 0.15);
              --blue: #60a5fa;
              --text: #e6edf3;
              --text-muted: #8b949e;
              --text-subtle: #6e7681;
              --radius: 0.5rem;
              --radius-lg: 1rem;
              --max-width: 1200px;
              --shadow-md: 0 4px 16px rgba(0,0,0,0.5);
            }
            *, *::before, *::after { box-sizing: border-box; }
            body {
              font-family: 'Inter', system-ui, -apple-system, sans-serif;
              background: var(--bg-primary); color: var(--text);
              margin: 0; padding: 0; line-height: 1.6;
            }
            h1, h2, h3 { font-family: 'Space Grotesk', sans-serif; margin: 0; }
            .container { max-width: var(--max-width); margin: 0 auto; padding: 0 1.5rem; }

            /* ── Header ─────────────────────────────────── */
            .page-header {
              padding: 3rem 0 2rem;
              background: radial-gradient(ellipse 80% 50% at 50% -10%, rgba(249,115,22,0.12) 0%, transparent 60%);
              border-bottom: 1px solid var(--border-subtle);
            }
            .page-header h1 {
              font-size: clamp(1.75rem, 4vw, 2.5rem); font-weight: 700;
              background: linear-gradient(135deg, #fff 0%, var(--orange) 60%, var(--amber) 100%);
              -webkit-background-clip: text; -webkit-text-fill-color: transparent;
              background-clip: text; margin-bottom: 1rem;
            }
            .meta { display: flex; gap: 1.5rem; flex-wrap: wrap; }
            .meta-item {
              font-size: 0.875rem; color: var(--text-muted);
              background: var(--bg-surface); padding: 0.25rem 0.75rem;
              border-radius: var(--radius); border: 1px solid var(--border-subtle);
            }
            .meta-label {
              font-size: 0.7rem; font-weight: 600; text-transform: uppercase;
              letter-spacing: 0.08em; color: var(--orange); margin-right: 0.5rem;
            }

            /* ── Stats ──────────────────────────────────── */
            .stats-section { padding: 2rem 0; }
            .stats-grid {
              display: flex; gap: 2rem; justify-content: center; flex-wrap: wrap;
            }
            .stat { text-align: center; min-width: 80px; }
            .stat-value {
              font-family: 'Space Grotesk', sans-serif;
              font-size: 2.25rem; font-weight: 700; color: var(--orange);
            }
            .stat-value.pass { color: var(--green); }
            .stat-value.partial { color: var(--orange); }
            .stat-value.fail { color: var(--red); }
            .stat-label { font-size: 0.875rem; color: var(--text-muted); }

            /* ── Filter bar ─────────────────────────────── */
            .filter-bar {
              display: flex; gap: 0.5rem; align-items: center; flex-wrap: wrap;
              padding: 0.75rem 1rem; background: var(--bg-surface);
              border: 1px solid var(--border-subtle); border-radius: var(--radius-lg);
              position: sticky; top: 0; z-index: 50;
              backdrop-filter: blur(12px);
            }
            .filter-label {
              font-size: 0.75rem; font-weight: 600; text-transform: uppercase;
              letter-spacing: 0.1em; color: var(--text-subtle); margin-right: 0.5rem;
            }
            .filter-btn {
              padding: 0.375rem 0.875rem; border-radius: var(--radius);
              border: 1px solid var(--border); background: transparent;
              color: var(--text-muted); cursor: pointer; font-size: 0.875rem;
              font-family: 'Space Grotesk', sans-serif; font-weight: 500;
              transition: all 0.2s ease;
            }
            .filter-btn:hover { background: var(--bg-elevated); color: var(--text); border-color: var(--text-subtle); }
            .filter-btn.active { background: var(--orange); color: #0d1117; border-color: var(--orange); }

            /* ── Category sections ──────────────────────── */
            .category { padding: 2.5rem 0 1rem; }
            .category.hidden { display: none; }
            .section-header { margin-bottom: 1.5rem; }
            .section-label {
              font-size: 0.75rem; font-weight: 600; text-transform: uppercase;
              letter-spacing: 0.1em; color: var(--orange); display: block; margin-bottom: 0.25rem;
            }
            .section-title { font-size: 1.5rem; font-weight: 700; color: var(--text); }
            .section-title .count { color: var(--text-subtle); font-weight: 400; }

            /* ── Card grid ──────────────────────────────── */
            .card-grid {
              display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
              gap: 1.25rem;
            }
            .card {
              background: var(--bg-surface); border: 1px solid var(--border-subtle);
              border-radius: var(--radius-lg); overflow: hidden; cursor: pointer;
              transition: all 0.2s ease;
            }
            .card:hover {
              border-color: var(--orange); transform: translateY(-2px);
              box-shadow: 0 8px 24px rgba(249,115,22,0.12);
            }
            .card-fail { border-color: rgba(248,113,113,0.4); }
            .card-fail:hover { border-color: var(--red); box-shadow: 0 8px 24px rgba(248,113,113,0.15); }
            .card-partial { border-color: rgba(249,115,22,0.4); }
            .card-partial:hover { border-color: var(--orange); box-shadow: 0 8px 24px rgba(249,115,22,0.15); }
            .card.hidden { display: none; }

            .card-thumbnail {
              aspect-ratio: 1; background: var(--bg-primary);
              display: flex; align-items: center; justify-content: center;
              border-bottom: 1px solid var(--border-subtle);
            }
            .card-thumbnail img {
              width: 100%; height: 100%; object-fit: contain;
              image-rendering: pixelated;
            }
            .card-no-image {
              color: var(--text-subtle); font-size: 0.85rem; font-style: italic;
            }
            .card-body { padding: 0.875rem 1rem; }
            .card-title {
              font-size: 0.9rem; font-weight: 600; margin-bottom: 0.5rem;
              white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
            }

            /* Backend badges */
            .backend-badges { display: flex; gap: 4px; margin-bottom: 0.5rem; }
            .backend-badge {
              font-size: 0.7rem; font-weight: 700; font-family: 'JetBrains Mono', monospace;
              padding: 2px 6px; border-radius: 4px; letter-spacing: 0.03em;
            }
            .badge-pass { background: var(--green-dim); color: var(--green); }
            .badge-fail { background: var(--red-dim); color: var(--red); }
            .badge-error { background: var(--red-dim); color: var(--red); }
            .badge-noref { background: var(--orange-dim); color: var(--orange-light); }
            .badge-skip { background: rgba(110,118,129,0.15); color: var(--text-subtle); }

            /* Compact matrix on card */
            .card-matrix { display: flex; gap: 4px; flex-wrap: wrap; }
            .mx {
              font-size: 0.65rem; font-family: 'JetBrains Mono', monospace;
              padding: 1px 5px; border-radius: 3px; white-space: nowrap;
            }
            .mx-pass { background: var(--green-dim); color: var(--green); }
            .mx-fail { background: var(--red-dim); color: var(--red); }
            .mx-known { background: var(--orange-dim); color: var(--orange-light); }

            /* ── Detail overlay ─────────────────────────── */
            .overlay {
              position: fixed; inset: 0; background: rgba(0,0,0,0.7);
              backdrop-filter: blur(4px); z-index: 100;
              display: flex; align-items: center; justify-content: center;
              padding: 2rem;
            }
            .overlay.hidden { display: none; }
            .detail-panel {
              background: var(--bg-surface); border: 1px solid var(--border);
              border-radius: var(--radius-lg); max-width: 900px; width: 100%;
              max-height: 85vh; overflow-y: auto; position: relative;
              box-shadow: var(--shadow-md);
            }
            .detail-close {
              position: sticky; top: 0; float: right; margin: 0.75rem;
              background: var(--bg-elevated); border: 1px solid var(--border);
              color: var(--text-muted); font-size: 1.5rem; width: 2rem; height: 2rem;
              border-radius: var(--radius); cursor: pointer; display: flex;
              align-items: center; justify-content: center; z-index: 1;
              transition: all 0.2s ease;
            }
            .detail-close:hover { color: var(--text); border-color: var(--text-subtle); }

            .detail-header {
              padding: 1.5rem 1.75rem 1rem; border-bottom: 1px solid var(--border-subtle);
            }
            .detail-header h2 { font-size: 1.5rem; font-weight: 700; margin-bottom: 0.25rem; }
            .detail-header .detail-class {
              font-size: 0.8rem; color: var(--text-subtle);
              font-family: 'JetBrains Mono', monospace;
            }

            .detail-body { padding: 1.5rem 1.75rem; }

            /* Detail: side-by-side screenshots */
            .detail-images {
              display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
              gap: 1rem; margin-bottom: 1.5rem;
            }
            .detail-img-card {
              text-align: center; background: var(--bg-primary);
              border: 1px solid var(--border-subtle); border-radius: var(--radius);
              padding: 0.5rem; overflow: hidden;
            }
            .detail-img-card img {
              width: 100%; aspect-ratio: 1; object-fit: contain;
              image-rendering: pixelated;
            }
            .detail-img-label {
              font-size: 0.8rem; font-weight: 600; color: var(--text-muted);
              margin-top: 0.5rem; text-transform: uppercase; letter-spacing: 0.05em;
            }

            /* Detail: reference comparison table */
            .detail-section-title {
              font-size: 0.75rem; font-weight: 600; text-transform: uppercase;
              letter-spacing: 0.1em; color: var(--orange); margin-bottom: 0.75rem;
            }
            .detail-table {
              width: 100%; border-collapse: collapse; font-size: 0.85rem;
              margin-bottom: 1.5rem;
            }
            .detail-table th {
              text-align: left; font-weight: 600; color: var(--text-muted);
              padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border);
              font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.05em;
            }
            .detail-table td {
              padding: 0.5rem 0.75rem; border-bottom: 1px solid var(--border-subtle);
              color: var(--text);
            }
            .detail-table tr:last-child td { border-bottom: none; }
            .status-pass { color: var(--green); font-weight: 600; }
            .status-fail { color: var(--red); font-weight: 600; }
            .status-noref { color: var(--orange-light); }
            .status-known { color: var(--orange-light); font-weight: 600; }
            .status-skip { color: var(--text-subtle); }

            /* Detail: error block */
            /* Known limitations callout */
            .known-limitations-callout {
              background: var(--orange-dim); border: 1px solid rgba(249,115,22,0.3);
              border-left: 3px solid var(--orange); border-radius: var(--radius);
              padding: 1rem 1.25rem; margin-bottom: 1.5rem;
            }
            .callout-title {
              font-family: 'Space Grotesk', sans-serif; font-size: 0.85rem;
              font-weight: 600; color: var(--orange); margin-bottom: 0.5rem;
              text-transform: uppercase; letter-spacing: 0.05em;
            }
            .callout-item {
              font-size: 0.85rem; color: var(--text-muted); margin-bottom: 0.25rem;
            }
            .callout-backend {
              font-family: 'JetBrains Mono', monospace; font-weight: 600;
              color: var(--orange-light); background: rgba(249,115,22,0.2);
              padding: 1px 5px; border-radius: 3px; font-size: 0.8rem;
            }

            .error-block {
              background: var(--bg-elevated); border: 1px solid var(--border-subtle);
              border-left: 3px solid var(--red); border-radius: var(--radius);
              padding: 1rem 1.25rem; margin-bottom: 1rem;
              font-family: 'JetBrains Mono', monospace; font-size: 0.8rem;
              color: var(--text-muted); white-space: pre-wrap;
              max-height: 200px; overflow-y: auto;
            }
            .error-block .error-title {
              color: var(--red); font-weight: 600; margin-bottom: 0.5rem;
              font-family: 'Space Grotesk', sans-serif; font-size: 0.85rem;
            }

            @media (max-width: 768px) {
              .card-grid { grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); }
              .detail-panel { margin: 1rem; max-height: 90vh; }
              .detail-images { grid-template-columns: 1fr 1fr; }
              .stats-grid { gap: 1rem; }
            }
            """);
    }

    private static void appendScript(StringBuilder sb) {
        sb.append("<script>\n");
        // Build scene data as JSON for detail view
        sb.append("const manifest = ").append("null").append(";\n");
        sb.append("""
            function setFilter(mode, btn) {
              document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
              btn.classList.add('active');
              document.querySelectorAll('.card').forEach(c => {
                var status = c.dataset.status;
                if (mode === 'all') c.classList.remove('hidden');
                else c.classList.toggle('hidden', status !== mode);
              });
              document.querySelectorAll('.category').forEach(cat => {
                var visible = cat.querySelectorAll('.card:not(.hidden)').length;
                cat.classList.toggle('hidden', visible === 0);
              });
            }

            function showDetail(sceneName) {
              var el = document.getElementById('detail-' + sceneName);
              if (!el) return;
              document.getElementById('detail-content').innerHTML = el.innerHTML;
              document.getElementById('detail-overlay').classList.remove('hidden');
              document.body.style.overflow = 'hidden';
            }

            function closeDetail() {
              document.getElementById('detail-overlay').classList.add('hidden');
              document.body.style.overflow = '';
            }

            document.addEventListener('keydown', e => { if (e.key === 'Escape') closeDetail(); });
            """);
        sb.append("</script>\n");
    }

    /** Entry point for Gradle JavaExec task. Args: manifestPath screenshotDir outputFile */
    public static void main(String[] args) throws Exception {
        generate(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
        System.out.println("Report: file://" + Path.of(args[2]).toAbsolutePath());
    }

    // ── Detail view templates (hidden, cloned into modal) ───────────────

    static void appendDetailTemplates(StringBuilder sb, Manifest manifest, List<String> allBackends) {
        for (var scene : manifest.scenes) {
            sb.append("<template id=\"detail-").append(esc(scene.name)).append("\">\n");

            // Header
            sb.append("<div class=\"detail-header\">\n");
            sb.append("  <h2>").append(formatSceneName(scene.name)).append("</h2>\n");
            sb.append("  <div class=\"detail-class\">").append(esc(scene.className))
              .append(".").append(esc(scene.fieldName)).append("</div>\n");
            sb.append("</div>\n");

            sb.append("<div class=\"detail-body\">\n");

            // Known limitations callout (always shown if any exist)
            if (!scene.knownLimitations.isEmpty()) {
                sb.append("<div class=\"known-limitations-callout\">\n");
                sb.append("  <div class=\"callout-title\">Known Limitations</div>\n");
                for (var kl : scene.knownLimitations) {
                    sb.append("  <div class=\"callout-item\">");
                    sb.append("<span class=\"callout-backend\">").append(esc(kl.backend())).append("</span> ");
                    sb.append(esc(kl.reason()));
                    sb.append("</div>\n");
                }
                sb.append("</div>\n");
            }

            // Screenshots side by side
            var runs = manifest.runs.stream()
                    .filter(r -> r.scene.equals(scene.name) && "success".equals(r.status))
                    .toList();
            if (!runs.isEmpty()) {
                sb.append("<div class=\"detail-section-title\">Screenshots</div>\n");
                sb.append("<div class=\"detail-images\">\n");
                for (var run : runs) {
                    for (var screenshot : run.screenshots) {
                        sb.append("<div class=\"detail-img-card\">\n");
                        sb.append("  <img src=\"").append(esc(screenshot.path())).append("\">\n");
                        sb.append("  <div class=\"detail-img-label\">").append(esc(run.backend));
                        if (run.screenshots.size() > 1 || scene.captureFrames.size() > 1) {
                            sb.append(" f").append(screenshot.frame());
                        }
                        sb.append("</div>\n");
                        sb.append("</div>\n");
                    }
                }
                sb.append("</div>\n");
            }

            // Reference comparisons table
            var refComps = manifest.comparisons.stream()
                    .filter(c -> c.scene.equals(scene.name) && "reference".equals(c.type))
                    .toList();
            if (!refComps.isEmpty()) {
                sb.append("<div class=\"detail-section-title\">Reference Comparisons</div>\n");
                sb.append("<table class=\"detail-table\">\n");
                sb.append("<tr><th>Backend</th><th>Status</th><th>Diff</th><th>Threshold</th></tr>\n");
                for (var comp : refComps) {
                    var statusClass = switch (comp.status) {
                        case "pass" -> "status-pass";
                        case "fail" -> "status-fail";
                        case "known_limitation" -> "status-known";
                        case "no_reference" -> "status-noref";
                        default -> "status-skip";
                    };
                    var statusLabel = switch (comp.status) {
                        case "pass" -> "Pass";
                        case "fail" -> "Fail";
                        case "known_limitation" -> "Known Limitation";
                        case "no_reference" -> "No Reference";
                        case "skipped" -> "Skipped";
                        default -> comp.status;
                    };
                    sb.append("<tr><td>").append(esc(comp.backend != null ? comp.backend : "—")).append("</td>");
                    sb.append("<td class=\"").append(statusClass).append("\">").append(statusLabel).append("</td>");
                    boolean hasDiff = "pass".equals(comp.status) || "fail".equals(comp.status) || "known_limitation".equals(comp.status);
                    sb.append("<td>").append(hasDiff ? String.format("%.4f%%", comp.diffPercent) : "—").append("</td>");
                    sb.append("<td>").append(comp.tolerance != null ? String.format("%.4f%%", comp.tolerance.maxDiffPercent()) : "—").append("</td>");
                    sb.append("</tr>\n");
                }
                sb.append("</table>\n");
            }

            // Cross-backend comparison matrix table
            var crossComps = manifest.comparisons.stream()
                    .filter(c -> c.scene.equals(scene.name) && "cross_backend".equals(c.type))
                    .toList();
            if (!crossComps.isEmpty()) {
                sb.append("<div class=\"detail-section-title\">Cross-Backend Comparison</div>\n");
                sb.append("<table class=\"detail-table\">\n");
                sb.append("<tr><th>Backends</th><th>Status</th><th>Diff</th><th>Threshold</th><th>Reason</th></tr>\n");
                for (var comp : crossComps) {
                    var statusClass = switch (comp.status) {
                        case "fail" -> "status-fail";
                        case "known_limitation" -> "status-known";
                        default -> "status-pass";
                    };
                    var statusLabel = switch (comp.status) {
                        case "fail" -> "Fail";
                        case "known_limitation" -> "Known";
                        default -> "Pass";
                    };
                    sb.append("<tr><td>").append(esc(comp.backendA)).append(" ↔ ").append(esc(comp.backendB)).append("</td>");
                    sb.append("<td class=\"").append(statusClass).append("\">").append(statusLabel).append("</td>");
                    sb.append("<td>").append(String.format("%.4f%%", comp.diffPercent)).append("</td>");
                    sb.append("<td>").append(comp.tolerance != null ? String.format("%.4f%%", comp.tolerance.maxDiffPercent()) : "—").append("</td>");
                    sb.append("<td class=\"").append(statusClass).append("\">")
                      .append(comp.reason != null ? esc(comp.reason) : "—").append("</td>");
                    sb.append("</tr>\n");
                }
                sb.append("</table>\n");
            }

            // Error details for failed runs
            var failedRuns = manifest.runs.stream()
                    .filter(r -> r.scene.equals(scene.name) && !"success".equals(r.status))
                    .toList();
            if (!failedRuns.isEmpty()) {
                sb.append("<div class=\"detail-section-title\">Errors</div>\n");
                for (var run : failedRuns) {
                    sb.append("<div class=\"error-block\">\n");
                    sb.append("<div class=\"error-title\">").append(esc(run.backend))
                      .append(" — ").append(esc(run.status)).append("</div>\n");
                    if (run.error != null) {
                        sb.append(esc(run.error.message()));
                        if (run.error.stderr() != null && !run.error.stderr().isEmpty()) {
                            sb.append("\n\n").append(esc(run.error.stderr()));
                        }
                    }
                    sb.append("</div>\n");
                }
            }

            // Run durations
            var allRuns = manifest.runs.stream()
                    .filter(r -> r.scene.equals(scene.name)).toList();
            if (!allRuns.isEmpty()) {
                sb.append("<div class=\"detail-section-title\">Run Times</div>\n");
                sb.append("<table class=\"detail-table\">\n");
                sb.append("<tr><th>Backend</th><th>Status</th><th>Duration</th></tr>\n");
                for (var run : allRuns) {
                    sb.append("<tr><td>").append(esc(run.backend)).append("</td>");
                    sb.append("<td>").append(esc(run.status)).append("</td>");
                    sb.append("<td>").append(run.durationMs).append("ms</td></tr>\n");
                }
                sb.append("</table>\n");
            }

            sb.append("</div>\n"); // detail-body
            sb.append("</template>\n\n");
        }
    }

    private static String formatSceneName(String name) {
        // depth_test_cubes → Depth Test Cubes
        var parts = name.split("_");
        var sb = new StringBuilder();
        for (var part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String backendShort(String backend) {
        return switch (backend) {
            case "opengl" -> "GL";
            case "vulkan" -> "VK";
            case "webgpu" -> "WG";
            default -> backend.substring(0, Math.min(2, backend.length())).toUpperCase();
        };
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
