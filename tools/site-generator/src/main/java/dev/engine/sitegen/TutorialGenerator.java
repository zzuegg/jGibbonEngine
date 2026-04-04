package dev.engine.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Generates Jekyll markdown pages from annotated tutorial Java sources.
 *
 * <p>Scans tutorial source files for {@code @Tutorial}, splits them into
 * prose (block comments) and code sections, and writes markdown files.
 *
 * <p>Run via: {@code ./gradlew :tools:site-generator:generateTutorials}
 */
public class TutorialGenerator {

    record TutorialInfo(String title, String category, int order, String description,
                        String slug, List<SectionParser.Section> sections) {}

    public static void main(String[] args) throws IOException {
        var sourceDir = args.length > 0 ? args[0] : "samples/tutorials/src/main/java";
        var outputDir = args.length > 1 ? args[1] : "docs/tutorials";

        var tutorials = new ArrayList<TutorialInfo>();

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

        var outPath = Path.of(outputDir);
        Files.createDirectories(outPath);

        for (var tutorial : tutorials) {
            var md = generateMarkdown(tutorial);
            Files.writeString(outPath.resolve(tutorial.slug + ".md"), md);
            System.out.println("  GENERATED " + tutorial.slug + ".md — " + tutorial.title);
        }

        var index = generateIndex(tutorials);
        Files.writeString(outPath.resolve("index.md"), index);
        System.out.println("  GENERATED tutorials/index.md");
        System.out.println("\n" + tutorials.size() + " tutorials generated in " + outputDir);
    }

    static TutorialInfo parseTutorial(Path file) throws IOException {
        var source = Files.readString(file);

        var annoPattern = Pattern.compile("@Tutorial\\s*\\(([^)]+)\\)", Pattern.DOTALL);
        var annoMatcher = annoPattern.matcher(source);
        if (!annoMatcher.find()) return null;

        var annoBody = annoMatcher.group(1);
        var title = AnnotationParser.extractString(annoBody, "title");
        var category = AnnotationParser.extractString(annoBody, "category");
        var description = AnnotationParser.extractString(annoBody, "description");
        var order = AnnotationParser.extractInt(annoBody, "order");

        if (title == null) return null;

        if (category == null || category.isEmpty()) {
            var pkg = AnnotationParser.extractPackage(source);
            if (pkg != null) {
                var parts = pkg.split("\\.");
                var last = parts[parts.length - 1];
                category = last.substring(0, 1).toUpperCase() + last.substring(1).replace('_', ' ');
            } else {
                category = "General";
            }
        }

        if (order < 0) {
            var filename = file.getFileName().toString();
            var orderMatch = Pattern.compile("^T?(\\d+)_").matcher(filename);
            order = orderMatch.find() ? Integer.parseInt(orderMatch.group(1)) : 99;
        }

        var slug = file.getFileName().toString()
                .replace(".java", "")
                .replaceAll("^T?\\d+_", "")
                .toLowerCase()
                .replace('_', '-');

        var sections = SectionParser.parse(source);

        return new TutorialInfo(title, category, order, description, slug, sections);
    }

    static String generateMarkdown(TutorialInfo tutorial) {
        var sb = new StringBuilder();
        sb.append("<!-- AUTO-GENERATED — do not edit, run ./gradlew :tools:site-generator:generateSite -->\n");
        sb.append("---\n");
        sb.append("layout: page\n");
        sb.append("title: \"").append(tutorial.title).append("\"\n");
        if (tutorial.description != null && !tutorial.description.isEmpty()) {
            sb.append("description: \"").append(tutorial.description).append("\"\n");
        }
        sb.append("---\n\n");

        for (var section : tutorial.sections) {
            switch (section) {
                case SectionParser.Prose p -> sb.append(p.text()).append("\n\n");
                case SectionParser.Code c -> sb.append("```java\n").append(c.code()).append("\n```\n\n");
            }
        }

        return sb.toString();
    }

    static String generateIndex(List<TutorialInfo> tutorials) {
        var sb = new StringBuilder();
        sb.append("<!-- AUTO-GENERATED — do not edit, run ./gradlew :tools:site-generator:generateSite -->\n");
        sb.append("---\n");
        sb.append("layout: page\n");
        sb.append("title: \"Tutorials\"\n");
        sb.append("description: \"Step-by-step guides to learn jGibbonEngine.\"\n");
        sb.append("---\n\n");

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
}
