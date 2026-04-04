package dev.engine.sitegen;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilities for extracting annotation values from Java source text.
 * Used by all generators to parse annotations without reflection.
 */
final class AnnotationParser {

    private AnnotationParser() {}

    static String extractString(String annoBody, String key) {
        var pattern = Pattern.compile(key + "\\s*=\\s*\"([^\"]*)\"");
        var matcher = pattern.matcher(annoBody);
        return matcher.find() ? matcher.group(1) : null;
    }

    static int extractInt(String annoBody, String key) {
        var pattern = Pattern.compile(key + "\\s*=\\s*(\\d+)");
        var matcher = pattern.matcher(annoBody);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : -1;
    }

    static List<String> extractStringArray(String annoBody, String key) {
        var pattern = Pattern.compile(key + "\\s*=\\s*\\{([^}]*)\\}");
        var matcher = pattern.matcher(annoBody);
        if (!matcher.find()) return List.of();

        var inner = matcher.group(1);
        var items = new ArrayList<String>();
        var itemPattern = Pattern.compile("\"([^\"]*)\"");
        var itemMatcher = itemPattern.matcher(inner);
        while (itemMatcher.find()) {
            items.add(itemMatcher.group(1));
        }
        return items;
    }

    static String extractPackage(String source) {
        var pattern = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
        var matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * Extracts the body of an annotation, handling parentheses inside string literals.
     * Returns the content between the outermost ( and ), or null if not found.
     */
    static String extractAnnotationBody(String source, String annotationName) {
        int idx = source.indexOf("@" + annotationName);
        if (idx < 0) return null;

        int parenStart = source.indexOf('(', idx);
        if (parenStart < 0) return null;

        int depth = 0;
        boolean inString = false;
        for (int i = parenStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (inString) {
                if (c == '\\') { i++; continue; } // skip escaped chars
                if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '(') depth++;
                else if (c == ')') {
                    depth--;
                    if (depth == 0) return source.substring(parenStart + 1, i);
                }
            }
        }
        return null;
    }

    /**
     * Finds all annotation bodies for a repeatable annotation.
     */
    static List<String> extractAllAnnotationBodies(String source, String annotationName) {
        var results = new ArrayList<String>();
        int searchFrom = 0;
        while (true) {
            int idx = source.indexOf("@" + annotationName, searchFrom);
            if (idx < 0) break;

            // Make sure this isn't @EngineFeatures (container) when looking for @EngineFeature
            int afterName = idx + 1 + annotationName.length();
            if (afterName < source.length() && Character.isLetterOrDigit(source.charAt(afterName))) {
                searchFrom = afterName;
                continue;
            }

            int parenStart = source.indexOf('(', idx);
            if (parenStart < 0) break;

            int depth = 0;
            boolean inString = false;
            int end = -1;
            for (int i = parenStart; i < source.length(); i++) {
                char c = source.charAt(i);
                if (inString) {
                    if (c == '\\') { i++; continue; }
                    if (c == '"') inString = false;
                } else {
                    if (c == '"') inString = true;
                    else if (c == '(') depth++;
                    else if (c == ')') {
                        depth--;
                        if (depth == 0) { end = i; break; }
                    }
                }
            }

            if (end > 0) {
                results.add(source.substring(parenStart + 1, end));
                searchFrom = end + 1;
            } else {
                break;
            }
        }
        return results;
    }
}
