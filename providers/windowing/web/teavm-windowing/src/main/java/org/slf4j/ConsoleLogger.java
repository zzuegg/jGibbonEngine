package org.slf4j;

/**
 * Minimal Logger implementation that outputs to System.out / System.err.
 *
 * <p>TeaVM maps System.out to console.log and System.err to console.error,
 * so this naturally routes to the browser dev-tools console.
 *
 * <p>SLF4J-style "{}" placeholders are expanded inline.
 */
final class ConsoleLogger implements Logger {

    private final String name;

    ConsoleLogger(String name) {
        this.name = name;
    }

    @Override public String getName() { return name; }

    // ---- formatting ----

    private static String format(String fmt, Object... args) {
        if (args == null || args.length == 0) return fmt;
        var sb = new StringBuilder(fmt.length() + 32);
        int argIdx = 0;
        int i = 0;
        while (i < fmt.length()) {
            if (i + 1 < fmt.length() && fmt.charAt(i) == '{' && fmt.charAt(i + 1) == '}') {
                if (argIdx < args.length) {
                    sb.append(args[argIdx++]);
                } else {
                    sb.append("{}");
                }
                i += 2;
            } else {
                sb.append(fmt.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    private void log(String level, String msg) {
        String line = "[" + level + "] " + name + " - " + msg;
        if ("ERROR".equals(level) || "WARN".equals(level)) {
            System.err.println(line);
        } else {
            System.out.println(line);
        }
    }

    private void log(String level, String fmt, Object arg) {
        log(level, format(fmt, arg));
    }

    private void log(String level, String fmt, Object arg1, Object arg2) {
        log(level, format(fmt, arg1, arg2));
    }

    private void logv(String level, String fmt, Object... args) {
        log(level, format(fmt, args));
    }

    private void log(String level, String msg, Throwable t) {
        log(level, msg);
        if (t != null) {
            System.err.println(t.toString());
        }
    }

    // ---- Trace ----
    @Override public boolean isTraceEnabled() { return true; }
    @Override public void trace(String msg) { log("TRACE", msg); }
    @Override public void trace(String f, Object a) { log("TRACE", f, a); }
    @Override public void trace(String f, Object a, Object b) { log("TRACE", f, a, b); }
    @Override public void trace(String f, Object... a) { logv("TRACE", f, a); }
    @Override public void trace(String m, Throwable t) { log("TRACE", m, t); }
    @Override public boolean isTraceEnabled(Marker m) { return true; }
    @Override public void trace(Marker m, String msg) { log("TRACE", msg); }
    @Override public void trace(Marker m, String f, Object a) { log("TRACE", f, a); }
    @Override public void trace(Marker m, String f, Object a, Object b) { log("TRACE", f, a, b); }
    @Override public void trace(Marker m, String f, Object... a) { logv("TRACE", f, a); }
    @Override public void trace(Marker m, String msg, Throwable t) { log("TRACE", msg, t); }

    // ---- Debug ----
    @Override public boolean isDebugEnabled() { return true; }
    @Override public void debug(String msg) { log("DEBUG", msg); }
    @Override public void debug(String f, Object a) { log("DEBUG", f, a); }
    @Override public void debug(String f, Object a, Object b) { log("DEBUG", f, a, b); }
    @Override public void debug(String f, Object... a) { logv("DEBUG", f, a); }
    @Override public void debug(String m, Throwable t) { log("DEBUG", m, t); }
    @Override public boolean isDebugEnabled(Marker m) { return true; }
    @Override public void debug(Marker m, String msg) { log("DEBUG", msg); }
    @Override public void debug(Marker m, String f, Object a) { log("DEBUG", f, a); }
    @Override public void debug(Marker m, String f, Object a, Object b) { log("DEBUG", f, a, b); }
    @Override public void debug(Marker m, String f, Object... a) { logv("DEBUG", f, a); }
    @Override public void debug(Marker m, String msg, Throwable t) { log("DEBUG", msg, t); }

    // ---- Info ----
    @Override public boolean isInfoEnabled() { return true; }
    @Override public void info(String msg) { log("INFO", msg); }
    @Override public void info(String f, Object a) { log("INFO", f, a); }
    @Override public void info(String f, Object a, Object b) { log("INFO", f, a, b); }
    @Override public void info(String f, Object... a) { logv("INFO", f, a); }
    @Override public void info(String m, Throwable t) { log("INFO", m, t); }
    @Override public boolean isInfoEnabled(Marker m) { return true; }
    @Override public void info(Marker m, String msg) { log("INFO", msg); }
    @Override public void info(Marker m, String f, Object a) { log("INFO", f, a); }
    @Override public void info(Marker m, String f, Object a, Object b) { log("INFO", f, a, b); }
    @Override public void info(Marker m, String f, Object... a) { logv("INFO", f, a); }
    @Override public void info(Marker m, String msg, Throwable t) { log("INFO", msg, t); }

    // ---- Warn ----
    @Override public boolean isWarnEnabled() { return true; }
    @Override public void warn(String msg) { log("WARN", msg); }
    @Override public void warn(String f, Object a) { log("WARN", f, a); }
    @Override public void warn(String f, Object... a) { logv("WARN", f, a); }
    @Override public void warn(String f, Object a, Object b) { log("WARN", f, a, b); }
    @Override public void warn(String m, Throwable t) { log("WARN", m, t); }
    @Override public boolean isWarnEnabled(Marker m) { return true; }
    @Override public void warn(Marker m, String msg) { log("WARN", msg); }
    @Override public void warn(Marker m, String f, Object a) { log("WARN", f, a); }
    @Override public void warn(Marker m, String f, Object a, Object b) { log("WARN", f, a, b); }
    @Override public void warn(Marker m, String f, Object... a) { logv("WARN", f, a); }
    @Override public void warn(Marker m, String msg, Throwable t) { log("WARN", msg, t); }

    // ---- Error ----
    @Override public boolean isErrorEnabled() { return true; }
    @Override public void error(String msg) { log("ERROR", msg); }
    @Override public void error(String f, Object a) { log("ERROR", f, a); }
    @Override public void error(String f, Object a, Object b) { log("ERROR", f, a, b); }
    @Override public void error(String f, Object... a) { logv("ERROR", f, a); }
    @Override public void error(String m, Throwable t) { log("ERROR", m, t); }
    @Override public boolean isErrorEnabled(Marker m) { return true; }
    @Override public void error(Marker m, String msg) { log("ERROR", msg); }
    @Override public void error(Marker m, String f, Object a) { log("ERROR", f, a); }
    @Override public void error(Marker m, String f, Object a, Object b) { log("ERROR", f, a, b); }
    @Override public void error(Marker m, String f, Object... a) { logv("ERROR", f, a); }
    @Override public void error(Marker m, String msg, Throwable t) { log("ERROR", msg, t); }
}
