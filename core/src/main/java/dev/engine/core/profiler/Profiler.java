package dev.engine.core.profiler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public class Profiler {

    private final Deque<ScopeEntry> scopeStack = new ArrayDeque<>();
    private Map<String, ProfileResult> currentFrame = new LinkedHashMap<>();
    private Map<String, ProfileResult> lastFrame = new LinkedHashMap<>();

    public ProfileScope scope(String name) {
        long start = System.nanoTime();
        var entry = new ScopeEntry(name, start, new LinkedHashMap<>());
        scopeStack.push(entry);
        return new ProfileScope(() -> endScope(entry));
    }

    public void newFrame() {
        lastFrame = currentFrame;
        currentFrame = new LinkedHashMap<>();
    }

    public Map<String, ProfileResult> lastFrame() {
        return currentFrame;
    }

    public Map<String, ProfileResult> previousFrame() {
        return lastFrame;
    }

    private void endScope(ScopeEntry entry) {
        long elapsed = System.nanoTime() - entry.startNanos;
        scopeStack.pop();
        var result = new ProfileResult(entry.name, elapsed, entry.children);

        if (scopeStack.isEmpty()) {
            currentFrame.put(entry.name, result);
        } else {
            scopeStack.peek().children.put(entry.name, result);
        }
    }

    private static class ScopeEntry {
        final String name;
        final long startNanos;
        final Map<String, ProfileResult> children;

        ScopeEntry(String name, long startNanos, Map<String, ProfileResult> children) {
            this.name = name;
            this.startNanos = startNanos;
            this.children = children;
        }
    }
}
