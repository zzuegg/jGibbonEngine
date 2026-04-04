package dev.engine.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Generates example showcase pages from {@code @Example} annotated Java sources.
 *
 * <p>Produces compact card-style pages with optional screenshot previews.
 */
public class ExamplePageGenerator {

    record ExampleInfo(String title, String description, String category, int order,
                       String screenshot, String slug, List<SectionParser.Section> sections) {}

    public static void main(String[] args) throws IOException {
        var sourceDir = args.length > 0 ? args[0] : "samples/examples/src/main/java";
        var docsDir = args.length > 1 ? args[1] : "docs";

        var examples = new ArrayList<ExampleInfo>();

        var sourcePath = Path.of(sourceDir);
        if (!Files.exists(sourcePath)) {
            System.out.println("Example source directory not found: " + sourceDir);
            System.out.println("Generating empty examples index.");
            var outPath = Path.of(docsDir, "examples");
            Files.createDirectories(outPath);
            Files.writeString(outPath.resolve("index.md"), generateIndex(examples));
            return;
        }

        try (var walk = Files.walk(sourcePath)) {
            walk.filter(p -> p.toString().endsWith(".java"))
                .sorted()
                .forEach(p -> {
                    try {
                        var info = parseExample(p);
                        if (info != null) examples.add(info);
                    } catch (IOException e) {
                        System.err.println("Error parsing " + p + ": " + e.getMessage());
                    }
                });
        }

        examples.sort(Comparator.comparing(ExampleInfo::category)
                .thenComparingInt(ExampleInfo::order)
                .thenComparing(ExampleInfo::title));

        var outPath = Path.of(docsDir, "examples");
        Files.createDirectories(outPath);

        for (var example : examples) {
            Files.writeString(outPath.resolve(example.slug + ".md"), generatePage(example));
            System.out.println("  GENERATED examples/" + example.slug + ".md — " + example.title);
        }

        Files.writeString(outPath.resolve("index.md"), generateIndex(examples));
        System.out.println("  GENERATED examples/index.md");
        System.out.println("\n" + examples.size() + " examples generated");
    }

    static ExampleInfo parseExample(Path file) throws IOException {
        var source = Files.readString(file);

        var annoPattern = Pattern.compile("@Example\\s*\\(([^)]+)\\)", Pattern.DOTALL);
        var annoMatcher = annoPattern.matcher(source);
        if (!annoMatcher.find()) return null;

        var body = annoMatcher.group(1);
        var title = AnnotationParser.extractString(body, "title");
        var description = AnnotationParser.extractString(body, "description");
        var category = AnnotationParser.extractString(body, "category");
        var order = AnnotationParser.extractInt(body, "order");
        var screenshot = AnnotationParser.extractString(body, "screenshot");

        if (title == null) return null;

        if (category == null || category.isEmpty()) category = "General";
        if (screenshot == null) screenshot = "";

        if (order < 0) {
            var filename = file.getFileName().toString();
            var orderMatch = Pattern.compile("^E?(\\d+)_").matcher(filename);
            order = orderMatch.find() ? Integer.parseInt(orderMatch.group(1)) : 99;
        }

        var slug = file.getFileName().toString()
                .replace(".java", "")
                .replaceAll("^E?\\d+_", "")
                .toLowerCase()
                .replace('_', '-');

        var sections = SectionParser.parse(source);

        return new ExampleInfo(title, description, category, order, screenshot, slug, sections);
    }

    private static String generatePage(ExampleInfo example) {
        var sb = new StringBuilder();
        sb.append("<!-- AUTO-GENERATED — do not edit, run ./gradlew :tools:site-generator:generateSite -->\n");
        sb.append("---\n");
        sb.append("layout: page\n");
        sb.append("title: \"").append(example.title).append("\"\n");
        if (!example.description.isEmpty()) {
            sb.append("description: \"").append(example.description).append("\"\n");
        }
        sb.append("---\n\n");

        // Screenshot preview
        if (!example.screenshot.isEmpty()) {
            sb.append("<div class=\"example-preview\">\n");
            sb.append("  <img src=\"{{ site.baseurl }}/assets/examples/")
              .append(example.screenshot)
              .append("\" alt=\"").append(example.title).append("\" />\n");
            sb.append("</div>\n\n");
        }

        // Category tag
        sb.append("<span class=\"index-card-label\">").append(example.category).append("</span>\n\n");

        if (!example.description.isEmpty()) {
            sb.append(example.description).append("\n\n");
        }

        for (var section : example.sections) {
            switch (section) {
                case SectionParser.Prose p -> sb.append(p.text()).append("\n\n");
                case SectionParser.Code c -> sb.append("```java\n").append(c.code()).append("\n```\n\n");
            }
        }

        return sb.toString();
    }

    private static String generateIndex(List<ExampleInfo> examples) {
        var sb = new StringBuilder();
        sb.append("<!-- AUTO-GENERATED — do not edit, run ./gradlew :tools:site-generator:generateSite -->\n");
        sb.append("---\n");
        sb.append("layout: page\n");
        sb.append("title: \"Examples\"\n");
        sb.append("description: \"Interactive showcases and code examples.\"\n");
        sb.append("---\n\n");

        if (examples.isEmpty()) {
            sb.append("Examples coming soon.\n");
            return sb.toString();
        }

        var byCategory = new LinkedHashMap<String, List<ExampleInfo>>();
        for (var e : examples) {
            byCategory.computeIfAbsent(e.category, k -> new ArrayList<>()).add(e);
        }

        for (var entry : byCategory.entrySet()) {
            sb.append("## ").append(entry.getKey()).append("\n\n");
            sb.append("<div class=\"index-grid\">\n");
            for (var e : entry.getValue()) {
                sb.append("  <a class=\"index-card example-card\" href=\"{{ site.baseurl }}/examples/")
                  .append(e.slug).append("\">\n");
                if (!e.screenshot.isEmpty()) {
                    sb.append("    <img class=\"example-thumb\" src=\"{{ site.baseurl }}/assets/examples/")
                      .append(e.screenshot).append("\" alt=\"").append(e.title).append("\" />\n");
                }
                sb.append("    <h3>").append(e.title).append("</h3>\n");
                if (!e.description.isEmpty()) {
                    sb.append("    <p>").append(e.description).append("</p>\n");
                }
                sb.append("  </a>\n");
            }
            sb.append("</div>\n\n");
        }

        return sb.toString();
    }
}
