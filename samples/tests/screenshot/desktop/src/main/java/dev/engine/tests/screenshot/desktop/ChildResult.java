package dev.engine.tests.screenshot.desktop;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON contract between the child render process and the runner.
 * The child writes this to a known path before exiting.
 */
public record ChildResult(String status, List<FrameCapture> screenshots,
                           String message, String stackTrace) {

    public record FrameCapture(int frame, String path) {}

    public static ChildResult success(List<FrameCapture> screenshots) {
        return new ChildResult("success", screenshots, null, null);
    }

    public static ChildResult exception(String message, String stackTrace) {
        return new ChildResult("exception", List.of(), message, stackTrace);
    }

    public void writeTo(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        var sb = new StringBuilder();
        sb.append("{");
        sb.append("\"status\":").append(jsonStr(status)).append(",");
        sb.append("\"screenshots\":[");
        for (int i = 0; i < screenshots.size(); i++) {
            var s = screenshots.get(i);
            sb.append("{\"frame\":").append(s.frame()).append(",\"path\":").append(jsonStr(s.path())).append("}");
            if (i < screenshots.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"message\":").append(message != null ? jsonStr(message) : "null").append(",");
        sb.append("\"stackTrace\":").append(stackTrace != null ? jsonStr(stackTrace) : "null");
        sb.append("}");
        Files.writeString(file, sb.toString());
    }

    public static ChildResult readFrom(Path file) throws IOException {
        var json = Files.readString(file);
        // Simple parsing for our well-defined format
        var status = extractString(json, "status");
        var message = extractNullableString(json, "message");
        var stackTrace = extractNullableString(json, "stackTrace");
        var screenshots = extractScreenshots(json);
        return new ChildResult(status, screenshots, message, stackTrace);
    }

    private static String extractString(String json, String key) {
        var search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return "";
        idx += search.length();
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        if (json.charAt(idx) == 'n') return null; // null
        return readJsonString(json, idx);
    }

    private static String extractNullableString(String json, String key) {
        var search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx += search.length();
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;
        if (json.charAt(idx) == 'n') return null;
        return readJsonString(json, idx);
    }

    private static List<FrameCapture> extractScreenshots(String json) {
        var list = new ArrayList<FrameCapture>();
        var search = "\"screenshots\":[";
        int idx = json.indexOf(search);
        if (idx < 0) return list;
        idx += search.length();
        while (idx < json.length() && json.charAt(idx) != ']') {
            if (json.charAt(idx) == '{') {
                int end = json.indexOf('}', idx);
                var obj = json.substring(idx, end + 1);
                int frame = extractInt(obj, "frame");
                String path = extractString(obj, "path");
                list.add(new FrameCapture(frame, path));
                idx = end + 1;
            } else {
                idx++;
            }
        }
        return list;
    }

    private static int extractInt(String json, String key) {
        var search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return 0;
        idx += search.length();
        int start = idx;
        while (idx < json.length() && (Character.isDigit(json.charAt(idx)) || json.charAt(idx) == '-')) idx++;
        return Integer.parseInt(json.substring(start, idx));
    }

    private static String readJsonString(String json, int pos) {
        if (json.charAt(pos) != '"') return "";
        pos++; // skip opening quote
        var sb = new StringBuilder();
        while (pos < json.length() && json.charAt(pos) != '"') {
            if (json.charAt(pos) == '\\') {
                pos++;
                switch (json.charAt(pos)) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> { sb.append('\\'); sb.append(json.charAt(pos)); }
                }
            } else {
                sb.append(json.charAt(pos));
            }
            pos++;
        }
        return sb.toString();
    }

    private static String jsonStr(String value) {
        if (value == null) return "null";
        var sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }
}
