package dev.engine.core.rendergraph;

import java.util.LinkedHashSet;
import java.util.Set;

public class PassBuilder {

    private final Set<String> reads = new LinkedHashSet<>();
    private final Set<String> writes = new LinkedHashSet<>();

    public void reads(String resource) { reads.add(resource); }
    public void writes(String resource) { writes.add(resource); }

    Set<String> getReads() { return reads; }
    Set<String> getWrites() { return writes; }
}
