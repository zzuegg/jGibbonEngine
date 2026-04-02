package dev.engine.core.profiler;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfileResult {

    private final String name;
    private final long cpuNanos;
    private long gpuNanos;
    private final Map<String, ProfileResult> children;

    ProfileResult(String name, long cpuNanos, Map<String, ProfileResult> children) {
        this.name = name;
        this.cpuNanos = cpuNanos;
        this.children = Collections.unmodifiableMap(children);
    }

    public String name() { return name; }
    public long cpuNanos() { return cpuNanos; }
    public double cpuMs() { return cpuNanos / 1_000_000.0; }
    public long gpuNanos() { return gpuNanos; }
    public double gpuMs() { return gpuNanos / 1_000_000.0; }
    public Map<String, ProfileResult> children() { return children; }

    void setGpuNanos(long gpuNanos) { this.gpuNanos = gpuNanos; }
}
