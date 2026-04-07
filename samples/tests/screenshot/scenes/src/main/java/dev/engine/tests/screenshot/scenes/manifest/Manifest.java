package dev.engine.tests.screenshot.scenes.manifest;

import dev.engine.tests.screenshot.scenes.Tolerance;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The screenshot test manifest — a single JSON file progressively enriched
 * by each pipeline pass (collect → run → compare → report).
 */
public class Manifest {

    // ── Top-level metadata ──────────────────────────────────────────────
    public String branch = "";
    public String commit = "";
    public String buildVersion = "";
    public String timestamp = "";
    public String javaVersion = "";
    public String os = "";
    public String profile = "local";

    // ── Pipeline data ───────────────────────────────────────────────────
    public final List<Scene> scenes = new ArrayList<>();
    public final List<Run> runs = new ArrayList<>();
    public final List<Comparison> comparisons = new ArrayList<>();

    // ── Inner types ─────────────────────────────────────────────────────

    public static class Scene {
        public String name = "";
        public String category = "";
        public String className = "";
        public String fieldName = "";
        public Set<Integer> captureFrames = Set.of(3);
        public Tolerance tolerance = Tolerance.loose();
        public int width = 256;
        public int height = 256;
    }

    public static class Run {
        public String scene = "";
        public String backend = "";
        public String status = "";
        public long durationMs;
        public List<Screenshot> screenshots = new ArrayList<>();
        public RunError error;
    }

    public record Screenshot(int frame, String path) {}

    public record RunError(String type, int exitCode, String message, String stderr, String stdout) {}

    public static class Comparison {
        public String scene = "";
        public int frame;
        public String type = "";
        // For reference comparisons
        public String backend;
        public String profile;
        // For cross-backend comparisons
        public String backendA;
        public String backendB;
        // Result
        public String status = "";
        public double diffPercent;
        public Tolerance tolerance;
        public String reason;
    }

    // ── Serialization ───────────────────────────────────────────────────

    public void writeTo(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, toJson());
    }

    public static Manifest readFrom(Path path) throws IOException {
        return fromJson(Files.readString(path));
    }

    String toJson() {
        var sb = new StringBuilder();
        sb.append("{\n");
        appendString(sb, "branch", branch, 1); sb.append(",\n");
        appendString(sb, "commit", commit, 1); sb.append(",\n");
        appendString(sb, "buildVersion", buildVersion, 1); sb.append(",\n");
        appendString(sb, "timestamp", timestamp, 1); sb.append(",\n");
        appendString(sb, "javaVersion", javaVersion, 1); sb.append(",\n");
        appendString(sb, "os", os, 1); sb.append(",\n");
        appendString(sb, "profile", profile, 1); sb.append(",\n");

        // Scenes
        sb.append("  \"scenes\": [\n");
        for (int i = 0; i < scenes.size(); i++) {
            writeScene(sb, scenes.get(i));
            if (i < scenes.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Runs
        sb.append("  \"runs\": [\n");
        for (int i = 0; i < runs.size(); i++) {
            writeRun(sb, runs.get(i));
            if (i < runs.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // Comparisons
        sb.append("  \"comparisons\": [\n");
        for (int i = 0; i < comparisons.size(); i++) {
            writeComparison(sb, comparisons.get(i));
            if (i < comparisons.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");

        sb.append("}\n");
        return sb.toString();
    }

    private void writeScene(StringBuilder sb, Scene s) {
        sb.append("    {");
        sb.append("\"name\":").append(jsonStr(s.name)).append(",");
        sb.append("\"category\":").append(jsonStr(s.category)).append(",");
        sb.append("\"className\":").append(jsonStr(s.className)).append(",");
        sb.append("\"fieldName\":").append(jsonStr(s.fieldName)).append(",");
        sb.append("\"captureFrames\":[");
        var frames = new ArrayList<>(s.captureFrames);
        Collections.sort(frames);
        for (int i = 0; i < frames.size(); i++) {
            sb.append(frames.get(i));
            if (i < frames.size() - 1) sb.append(",");
        }
        sb.append("],");
        sb.append("\"tolerance\":{\"maxChannelDiff\":").append(s.tolerance.maxChannelDiff())
          .append(",\"maxDiffPercent\":").append(s.tolerance.maxDiffPercent()).append("},");
        sb.append("\"width\":").append(s.width).append(",");
        sb.append("\"height\":").append(s.height);
        sb.append("}");
    }

    private void writeRun(StringBuilder sb, Run r) {
        sb.append("    {");
        sb.append("\"scene\":").append(jsonStr(r.scene)).append(",");
        sb.append("\"backend\":").append(jsonStr(r.backend)).append(",");
        sb.append("\"status\":").append(jsonStr(r.status)).append(",");
        sb.append("\"durationMs\":").append(r.durationMs).append(",");
        sb.append("\"screenshots\":[");
        for (int i = 0; i < r.screenshots.size(); i++) {
            var s = r.screenshots.get(i);
            sb.append("{\"frame\":").append(s.frame()).append(",\"path\":").append(jsonStr(s.path())).append("}");
            if (i < r.screenshots.size() - 1) sb.append(",");
        }
        sb.append("],");
        if (r.error != null) {
            sb.append("\"error\":{");
            sb.append("\"type\":").append(jsonStr(r.error.type())).append(",");
            sb.append("\"exitCode\":").append(r.error.exitCode()).append(",");
            sb.append("\"message\":").append(jsonStr(r.error.message())).append(",");
            sb.append("\"stderr\":").append(jsonStr(r.error.stderr())).append(",");
            sb.append("\"stdout\":").append(jsonStr(r.error.stdout()));
            sb.append("}");
        } else {
            sb.append("\"error\":null");
        }
        sb.append("}");
    }

    private void writeComparison(StringBuilder sb, Comparison c) {
        sb.append("    {");
        sb.append("\"scene\":").append(jsonStr(c.scene)).append(",");
        sb.append("\"frame\":").append(c.frame).append(",");
        sb.append("\"type\":").append(jsonStr(c.type)).append(",");
        sb.append("\"backend\":").append(c.backend != null ? jsonStr(c.backend) : "null").append(",");
        sb.append("\"profile\":").append(c.profile != null ? jsonStr(c.profile) : "null").append(",");
        sb.append("\"backendA\":").append(c.backendA != null ? jsonStr(c.backendA) : "null").append(",");
        sb.append("\"backendB\":").append(c.backendB != null ? jsonStr(c.backendB) : "null").append(",");
        sb.append("\"status\":").append(jsonStr(c.status)).append(",");
        sb.append("\"diffPercent\":").append(c.diffPercent).append(",");
        if (c.tolerance != null) {
            sb.append("\"tolerance\":{\"maxChannelDiff\":").append(c.tolerance.maxChannelDiff())
              .append(",\"maxDiffPercent\":").append(c.tolerance.maxDiffPercent()).append("},");
        } else {
            sb.append("\"tolerance\":null,");
        }
        sb.append("\"reason\":").append(c.reason != null ? jsonStr(c.reason) : "null");
        sb.append("}");
    }

    private void appendString(StringBuilder sb, String key, String value, int indent) {
        sb.append("  ".repeat(indent));
        sb.append("\"").append(key).append("\": ").append(jsonStr(value));
    }

    static String jsonStr(String value) {
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

    // ── Deserialization ─────────────────────────────────────────────────

    static Manifest fromJson(String json) {
        var parser = new JsonParser(json);
        return parser.parseManifest();
    }

    /**
     * Minimal JSON parser for our well-defined manifest format.
     * Not a general-purpose parser — handles exactly the structures we write.
     */
    static class JsonParser {
        private final String json;
        private int pos;

        JsonParser(String json) {
            this.json = json;
            this.pos = 0;
        }

        Manifest parseManifest() {
            var m = new Manifest();
            skipWhitespace();
            expect('{');
            while (peek() != '}') {
                var key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "branch" -> m.branch = readString();
                    case "commit" -> m.commit = readString();
                    case "buildVersion" -> m.buildVersion = readString();
                    case "timestamp" -> m.timestamp = readString();
                    case "javaVersion" -> m.javaVersion = readString();
                    case "os" -> m.os = readString();
                    case "profile" -> m.profile = readString();
                    case "scenes" -> parseScenes(m.scenes);
                    case "runs" -> parseRuns(m.runs);
                    case "comparisons" -> parseComparisons(m.comparisons);
                    default -> skipValue();
                }
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect('}');
            return m;
        }

        private void parseScenes(List<Scene> list) {
            expect('[');
            skipWhitespace();
            while (peek() != ']') {
                list.add(parseScene());
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect(']');
        }

        private Scene parseScene() {
            var s = new Scene();
            expect('{');
            while (peek() != '}') {
                var key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "name" -> s.name = readString();
                    case "category" -> s.category = readString();
                    case "className" -> s.className = readString();
                    case "fieldName" -> s.fieldName = readString();
                    case "captureFrames" -> s.captureFrames = parseIntSet();
                    case "tolerance" -> s.tolerance = parseTolerance();
                    case "width" -> s.width = readInt();
                    case "height" -> s.height = readInt();
                    default -> skipValue();
                }
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect('}');
            return s;
        }

        private void parseRuns(List<Run> list) {
            expect('[');
            skipWhitespace();
            while (peek() != ']') {
                list.add(parseRun());
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect(']');
        }

        private Run parseRun() {
            var r = new Run();
            expect('{');
            while (peek() != '}') {
                var key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "scene" -> r.scene = readString();
                    case "backend" -> r.backend = readString();
                    case "status" -> r.status = readString();
                    case "durationMs" -> r.durationMs = readLong();
                    case "screenshots" -> r.screenshots = parseScreenshots();
                    case "error" -> r.error = parseRunError();
                    default -> skipValue();
                }
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect('}');
            return r;
        }

        private List<Screenshot> parseScreenshots() {
            var list = new ArrayList<Screenshot>();
            expect('[');
            skipWhitespace();
            while (peek() != ']') {
                int frame = 0;
                String path = "";
                expect('{');
                while (peek() != '}') {
                    var key = readString();
                    skipWhitespace();
                    expect(':');
                    skipWhitespace();
                    switch (key) {
                        case "frame" -> frame = readInt();
                        case "path" -> path = readString();
                        default -> skipValue();
                    }
                    skipWhitespace();
                    if (peek() == ',') advance();
                    skipWhitespace();
                }
                expect('}');
                list.add(new Screenshot(frame, path));
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect(']');
            return list;
        }

        private RunError parseRunError() {
            if (tryNull()) return null;
            String type = "", message = "", stderr = "", stdout = "";
            int exitCode = 0;
            expect('{');
            while (peek() != '}') {
                var key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "type" -> type = readString();
                    case "exitCode" -> exitCode = readInt();
                    case "message" -> message = readString();
                    case "stderr" -> stderr = readString();
                    case "stdout" -> stdout = readString();
                    default -> skipValue();
                }
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect('}');
            return new RunError(type, exitCode, message, stderr, stdout);
        }

        private void parseComparisons(List<Comparison> list) {
            expect('[');
            skipWhitespace();
            while (peek() != ']') {
                list.add(parseComparison());
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect(']');
        }

        private Comparison parseComparison() {
            var c = new Comparison();
            expect('{');
            while (peek() != '}') {
                var key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "scene" -> c.scene = readString();
                    case "frame" -> c.frame = readInt();
                    case "type" -> c.type = readString();
                    case "backend" -> c.backend = readNullableString();
                    case "profile" -> c.profile = readNullableString();
                    case "backendA" -> c.backendA = readNullableString();
                    case "backendB" -> c.backendB = readNullableString();
                    case "status" -> c.status = readString();
                    case "diffPercent" -> c.diffPercent = readDouble();
                    case "tolerance" -> c.tolerance = parseNullableTolerance();
                    case "reason" -> c.reason = readNullableString();
                    default -> skipValue();
                }
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect('}');
            return c;
        }

        private Tolerance parseTolerance() {
            int maxChannelDiff = 0;
            double maxDiffPercent = 0;
            expect('{');
            while (peek() != '}') {
                var key = readString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                switch (key) {
                    case "maxChannelDiff" -> maxChannelDiff = readInt();
                    case "maxDiffPercent" -> maxDiffPercent = readDouble();
                    default -> skipValue();
                }
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect('}');
            return new Tolerance(maxChannelDiff, maxDiffPercent);
        }

        private Tolerance parseNullableTolerance() {
            if (tryNull()) return null;
            return parseTolerance();
        }

        private Set<Integer> parseIntSet() {
            var set = new HashSet<Integer>();
            expect('[');
            skipWhitespace();
            while (peek() != ']') {
                set.add(readInt());
                skipWhitespace();
                if (peek() == ',') advance();
                skipWhitespace();
            }
            expect(']');
            return set;
        }

        private String readString() {
            skipWhitespace();
            expect('"');
            var sb = new StringBuilder();
            while (json.charAt(pos) != '"') {
                char c = advance();
                if (c == '\\') {
                    char esc = advance();
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '/' -> sb.append('/');
                        default -> { sb.append('\\'); sb.append(esc); }
                    }
                } else {
                    sb.append(c);
                }
            }
            expect('"');
            return sb.toString();
        }

        private String readNullableString() {
            skipWhitespace();
            if (tryNull()) return null;
            return readString();
        }

        private int readInt() {
            skipWhitespace();
            int start = pos;
            if (peek() == '-') advance();
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            return Integer.parseInt(json.substring(start, pos));
        }

        private long readLong() {
            skipWhitespace();
            int start = pos;
            if (peek() == '-') advance();
            while (pos < json.length() && Character.isDigit(json.charAt(pos))) pos++;
            return Long.parseLong(json.substring(start, pos));
        }

        private double readDouble() {
            skipWhitespace();
            int start = pos;
            if (peek() == '-') advance();
            while (pos < json.length() && (Character.isDigit(json.charAt(pos))
                    || json.charAt(pos) == '.' || json.charAt(pos) == 'E'
                    || json.charAt(pos) == 'e' || json.charAt(pos) == '+')) pos++;
            return Double.parseDouble(json.substring(start, pos));
        }

        private boolean tryNull() {
            skipWhitespace();
            if (pos + 4 <= json.length() && json.startsWith("null", pos)) {
                pos += 4;
                return true;
            }
            return false;
        }

        private void skipValue() {
            skipWhitespace();
            char c = peek();
            if (c == '"') readString();
            else if (c == '{') skipObject();
            else if (c == '[') skipArray();
            else if (c == 'n') { pos += 4; } // null
            else if (c == 't') { pos += 4; } // true
            else if (c == 'f') { pos += 5; } // false
            else { // number
                while (pos < json.length() && ",}] \t\n\r".indexOf(json.charAt(pos)) == -1) pos++;
            }
        }

        private void skipObject() {
            expect('{');
            int depth = 1;
            while (depth > 0) {
                char c = advance();
                if (c == '{') depth++;
                else if (c == '}') depth--;
                else if (c == '"') { pos--; readString(); }
            }
        }

        private void skipArray() {
            expect('[');
            int depth = 1;
            while (depth > 0) {
                char c = advance();
                if (c == '[') depth++;
                else if (c == ']') depth--;
                else if (c == '"') { pos--; readString(); }
            }
        }

        private void skipWhitespace() {
            while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) pos++;
        }

        private char peek() {
            skipWhitespace();
            return json.charAt(pos);
        }

        private char advance() {
            return json.charAt(pos++);
        }

        private void expect(char c) {
            skipWhitespace();
            if (json.charAt(pos) != c) {
                throw new IllegalStateException("Expected '" + c + "' at pos " + pos
                        + " but got '" + json.charAt(pos) + "'");
            }
            pos++;
        }
    }
}
