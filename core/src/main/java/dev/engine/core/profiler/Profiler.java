package dev.engine.core.profiler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Profiler {

    private final ThreadLocal<Deque<ScopeEntry>> scopeStack =
            ThreadLocal.withInitial(ArrayDeque::new);
    private volatile Map<String, ProfileResult> currentFrame = new ConcurrentHashMap<>();
    private volatile Map<String, ProfileResult> lastFrame = new ConcurrentHashMap<>();

    public ProfileScope scope(String name) {
        long start = System.nanoTime();
        var entry = new ScopeEntry(name, start, new LinkedHashMap<>());
        scopeStack.get().push(entry);
        return new ProfileScope(() -> endScope(entry));
    }

    public void newFrame() {
        lastFrame = currentFrame;
        currentFrame = new ConcurrentHashMap<>();
    }

    public Map<String, ProfileResult> lastFrame() {
        return currentFrame;
    }

    public Map<String, ProfileResult> previousFrame() {
        return lastFrame;
    }

    private void endScope(ScopeEntry entry) {
        long elapsed = System.nanoTime() - entry.startNanos;
        var stack = scopeStack.get();
        stack.pop();
        var result = new ProfileResult(entry.name, elapsed, entry.children);

        if (stack.isEmpty()) {
            currentFrame.put(entry.name, result);
        } else {
            stack.peek().children.put(entry.name, result);
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
