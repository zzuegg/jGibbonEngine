package dev.engine.sitegen;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Java source into interleaved prose (block comments) and code sections.
 * Shared by TutorialGenerator and ExamplePageGenerator.
 */
final class SectionParser {

    sealed interface Section {}
    record Prose(String text) implements Section {}
    record Code(String code) implements Section {}

    private SectionParser() {}

    static List<Section> parse(String source) {
        var sections = new ArrayList<Section>();

        // Remove package statement and imports
        source = source.replaceAll("(?m)^package .*;\n", "");
        source = source.replaceAll("(?m)^import .*;\n", "");

        // Remove annotation lines (@Tutorial, @Example, @EngineModule, etc.)
        source = source.replaceAll("@\\w+\\s*\\([^)]*\\)\\s*\n?", "");

        // Remove the class declaration line and the final closing brace
        source = source.replaceAll("(?m)^public class \\w+ extends \\w+\\s*\\{\\s*\n?", "");
        source = source.replaceAll("(?m)^\\}\\s*$", "");

        int pos = 0;
        while (pos < source.length()) {
            int commentStart = source.indexOf("/*", pos);
            if (commentStart < 0) {
                var code = source.substring(pos).trim();
                if (!code.isEmpty()) sections.add(new Code(cleanCode(code)));
                break;
            }

            var codeBefore = source.substring(pos, commentStart).trim();
            if (!codeBefore.isEmpty()) sections.add(new Code(cleanCode(codeBefore)));

            int commentEnd = source.indexOf("*/", commentStart);
            if (commentEnd < 0) break;
            commentEnd += 2;

            var comment = source.substring(commentStart + 2, commentEnd - 2);
            var prose = cleanProse(comment);
            if (!prose.isEmpty()) sections.add(new Prose(prose));

            pos = commentEnd;
        }

        return sections;
    }

    private static String cleanProse(String comment) {
        var lines = comment.split("\n");
        var sb = new StringBuilder();
        for (var line : lines) {
            var cleaned = line.replaceFirst("^\\s*\\*? ?", "");
            sb.append(cleaned).append("\n");
        }
        return sb.toString().trim();
    }

    private static String cleanCode(String code) {
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
        var result = sb.toString();
        while (result.endsWith("\n\n")) result = result.substring(0, result.length() - 1);
        return result.trim();
    }
}
