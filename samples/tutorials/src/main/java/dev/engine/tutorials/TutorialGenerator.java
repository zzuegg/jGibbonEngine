package dev.engine.tutorials;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Generates Jekyll markdown pages from annotated tutorial Java sources.
 *
 * <p>Scans tutorial source files, splits them into prose (block comments)
 * and code sections, and writes markdown files to docs/tutorials/.
 *
 * <p>Run via: {@code ./gradlew :samples:tutorials:generateTutorials}
 */
public class TutorialGenerator {

    record TutorialInfo(String title, String category, int order, String description,
                         String slug, List<Section> sections) {}

    sealed interface Section {}
    record Prose(String text) implements Section {}
    record Code(String code) implements Section {}

    public static void main(String[] args) throws IOException {
        var sourceDir = args.length > 0 ? args[0] : "samples/tutorials/src/main/java";
        var outputDir = args.length > 1 ? args[1] : "docs/tutorials";

        var tutorials = new ArrayList<TutorialInfo>();

        // Scan for .java files with @Tutorial annotation
        try (var walk = Files.walk(Path.of(sourceDir))) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals("TutorialGenerator.java"))
                .sorted()
                .forEach(p -> {
                    try {
                        var info = parseTutorial(p);
                        if (info != null) tutorials.add(info);
                    } catch (IOException e) {
                        System.err.println("Error parsing " + p + ": " + e.getMessage());
                    }
                });
        }

        tutorials.sort(Comparator.comparing(TutorialInfo::category)
                .thenComparingInt(TutorialInfo::order)
                .thenComparing(TutorialInfo::title));

        // Generate individual tutorial pages
        var outPath = Path.of(outputDir);
        Files.createDirectories(outPath);

        for (var tutorial : tutorials) {
            var md = generateMarkdown(tutorial);
            Files.writeString(outPath.resolve(tutorial.slug + ".md"), md);
            System.out.println("  GENERATED " + tutorial.slug + ".md — " + tutorial.title);
        }

        // Generate index page
        var index = generateIndex(tutorials);
        Files.writeString(outPath.resolve("index.md"), index);
        System.out.println("  GENERATED index.md");

        System.out.println("\n" + tutorials.size() + " tutorials generated in " + outputDir);
    }

    static TutorialInfo parseTutorial(Path file) throws IOException {
        var source = Files.readString(file);

        // Extract @Tutorial annotation
        var annoPattern = Pattern.compile(
                "@Tutorial\\s*\\(([^)]+)\\)", Pattern.DOTALL);
        var annoMatcher = annoPattern.matcher(source);
        if (!annoMatcher.find()) return null;

        var annoBody = annoMatcher.group(1);
        var title = extractAnnoString(annoBody, "title");
        var category = extractAnnoString(annoBody, "category");
        var description = extractAnnoString(annoBody, "description");
        var order = extractAnnoInt(annoBody, "order");

        if (title == null) return null;

        // Derive category from package if not specified
        if (category == null || category.isEmpty()) {
            var pkg = extractPackage(source);
            if (pkg != null) {
                var parts = pkg.split("\\.");
                var last = parts[parts.length - 1];
                category = last.substring(0, 1).toUpperCase() + last.substring(1).replace('_', ' ');
            } else {
                category = "General";
            }
        }

        // Derive order from filename prefix (T01_, T02_)
        if (order < 0) {
            var filename = file.getFileName().toString();
            var orderMatch = Pattern.compile("^T?(\\d+)_").matcher(filename);
            order = orderMatch.find() ? Integer.parseInt(orderMatch.group(1)) : 99;
        }

        // Derive slug from filename
        var slug = file.getFileName().toString()
                .replace(".java", "")
                .replaceAll("^T?\\d+_", "")
                .toLowerCase()
                .replace('_', '-');

        // Parse sections: split source into prose (block comments) and code
        var sections = parseSections(source);

        return new TutorialInfo(title, category, order, description, slug, sections);
    }

    static List<Section> parseSections(String source) {
        var sections = new ArrayList<Section>();

        // Remove package statement and imports
        source = source.replaceAll("(?m)^package .*;\n", "");
        source = source.replaceAll("(?m)^import .*;\n", "");

        // Remove @Tutorial annotation line(s)
        source = source.replaceAll("@Tutorial\\s*\\([^)]*\\)\\s*\n?", "");

        // Remove the class declaration line and the final closing brace
        source = source.replaceAll("(?m)^public class \\w+ extends \\w+\\s*\\{\\s*\n?", "");
        source = source.replaceAll("(?m)^\\}\\s*$", "");

        // Now split into block comments (prose) and code
        int pos = 0;
        while (pos < source.length()) {
            int commentStart = source.indexOf("/*", pos);
            if (commentStart < 0) {
                // Rest is code
                var code = source.substring(pos).trim();
                if (!code.isEmpty()) sections.add(new Code(cleanCode(code)));
                break;
            }

            // Code before the comment
            var codeBefore = source.substring(pos, commentStart).trim();
            if (!codeBefore.isEmpty()) sections.add(new Code(cleanCode(codeBefore)));

            // Find end of comment
            int commentEnd = source.indexOf("*/", commentStart);
            if (commentEnd < 0) break;
            commentEnd += 2;

            // Extract comment text, strip leading * and whitespace
            var comment = source.substring(commentStart + 2, commentEnd - 2);
            var prose = cleanProse(comment);
            if (!prose.isEmpty()) sections.add(new Prose(prose));

            pos = commentEnd;
        }

        return sections;
    }

    static String cleanProse(String comment) {
        var lines = comment.split("\n");
        var sb = new StringBuilder();
        for (var line : lines) {
            // Remove leading whitespace + optional *
            var cleaned = line.replaceFirst("^\\s*\\*? ?", "");
            sb.append(cleaned).append("\n");
        }
        return sb.toString().trim();
    }

    static String cleanCode(String code) {
        // Remove empty lines at start/end, dedent
        var lines = code.split("\n");
        int minIndent = Integer.MAX_VALUE;
        for (var line : lines) {
            if (line.isBlank()) continue;
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') indent++;
            minIndent = Math.min(minIndent, indent);
        }
        if (minIndent == Integer.MAX_VALUE) minIndent = 0;

        var sb = new StringBuilder();
        for (var line : lines) {
            if (line.length() > minIndent) {
                sb.append(line.substring(minIndent));
            }
            sb.append("\n");
        }
        // Trim trailing blank lines
        var result = sb.toString();
        while (result.endsWith("\n\n")) result = result.substring(0, result.length() - 1);
        return result.trim();
    }

    static String generateMarkdown(TutorialInfo tutorial) {
        var sb = new StringBuilder();
        sb.append("---\n");
        sb.append("layout: page\n");
        sb.append("title: \"").append(tutorial.title).append("\"\n");
        if (tutorial.description != null && !tutorial.description.isEmpty()) {
            sb.append("description: \"").append(tutorial.description).append("\"\n");
        }
        sb.append("---\n\n");

        for (var section : tutorial.sections) {
            switch (section) {
                case Prose p -> sb.append(p.text()).append("\n\n");
                case Code c -> sb.append("```java\n").append(c.code()).append("\n```\n\n");
            }
        }

        return sb.toString();
    }

    static String generateIndex(List<TutorialInfo> tutorials) {
        var sb = new StringBuilder();
        sb.append("---\n");
        sb.append("layout: page\n");
        sb.append("title: \"Tutorials\"\n");
        sb.append("description: \"Step-by-step guides to learn jGibbonEngine.\"\n");
        sb.append("---\n\n");

        // Group by category
        var byCategory = new LinkedHashMap<String, List<TutorialInfo>>();
        for (var t : tutorials) {
            byCategory.computeIfAbsent(t.category, k -> new ArrayList<>()).add(t);
        }

        for (var entry : byCategory.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");
            sb.append("<div class=\"index-grid\">\n");
            for (var t : entry.getValue()) {
                sb.append("  <a class=\"index-card\" href=\"{{ site.baseurl }}/tutorials/")
                  .append(t.slug).append("\">\n");
                sb.append("    <h3>").append(t.title).append("</h3>\n");
                if (t.description != null && !t.description.isEmpty()) {
                    sb.append("    <p>").append(t.description).append("</p>\n");
                }
                sb.append("  </a>\n");
            }
            sb.append("</div>\n\n");
        }

        return sb.toString();
    }

    private static String extractAnnoString(String annoBody, String key) {
        var pattern = Pattern.compile(key + "\\s*=\\s*\"([^\"]*)\"");
        var matcher = pattern.matcher(annoBody);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static int extractAnnoInt(String annoBody, String key) {
        var pattern = Pattern.compile(key + "\\s*=\\s*(\\d+)");
        var matcher = pattern.matcher(annoBody);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    private static String extractPackage(String source) {
        var pattern = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
        var matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }
}
