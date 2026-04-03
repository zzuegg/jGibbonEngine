package dev.engine.core.shader;

import java.util.*;

/**
 * Registry of global shader parameter blocks.
 *
 * <p>Each entry maps a name (e.g., "Engine", "Camera", "Light") to a Java record type
 * and a fixed binding index. The registry generates Slang code for all registered blocks
 * and holds per-frame data for upload.
 *
 * <p>Engine registers defaults; users add their own without modifying engine code:
 * <pre>{@code
 * registry.register("Light", LightParams.class, 2);
 * registry.update("Light", new LightParams(dir, color, 1.0f));
 * }</pre>
 */
public final class GlobalParamsRegistry {

    private final List<Entry> entries = new ArrayList<>();
    private final Map<String, Entry> byName = new LinkedHashMap<>();
    private final Set<Integer> usedBindings = new HashSet<>();

    /**
     * Registers a global param block.
     *
     * @param name        the block name (e.g., "Camera") — used for Slang naming and lookup
     * @param recordType  the Java record defining the fields
     * @param binding     the fixed UBO binding index (register(bN))
     */
    public void register(String name, Class<?> recordType, int binding) {
        if (byName.containsKey(name)) {
            throw new IllegalArgumentException("Global params already registered: " + name);
        }
        if (usedBindings.contains(binding)) {
            throw new IllegalArgumentException("Binding " + binding + " already in use");
        }
        var entry = new Entry(name, recordType, binding, null);
        entries.add(entry);
        byName.put(name, entry);
        usedBindings.add(binding);
    }

    /**
     * Updates the per-frame data for a registered param block.
     */
    public void update(String name, Object data) {
        var entry = byName.get(name);
        if (entry == null) {
            throw new IllegalArgumentException("Unknown global params: " + name);
        }
        if (!entry.recordType.isInstance(data)) {
            throw new IllegalArgumentException("Expected " + entry.recordType.getSimpleName()
                    + " but got " + data.getClass().getSimpleName());
        }
        var updated = new Entry(entry.name, entry.recordType, entry.binding, data);
        entries.set(entries.indexOf(entry), updated);
        byName.put(name, updated);
    }

    /**
     * Returns all registered entries in registration order.
     */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    /**
     * Generates the combined Slang source for all registered global param blocks.
     * Each block gets interface + struct + cbuffer (with register) + impl + static global.
     */
    public String generateSlang() {
        return generateSlang(true);
    }

    /**
     * Generates the combined Slang source for all registered global param blocks.
     *
     * @param includeGlobals if true, emits static global instances (for process compiler fallback).
     *                       If false, omits globals — shader uses generic specialization instead.
     */
    public String generateSlang(boolean includeGlobals) {
        var sb = new StringBuilder();
        for (var entry : entries) {
            sb.append(SlangParamsBlock.fromRecord(entry.name, entry.recordType)
                    .withBinding(entry.binding)
                    .generateUbo(includeGlobals));
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns the concrete UBO type names for all registered blocks, in registration order.
     * Used as specialization arguments when compiling with the native Slang compiler.
     * E.g., ["UboEngineParams", "UboCameraParams", "UboObjectParams"]
     */
    public String[] specializationArgs() {
        return entries.stream()
                .map(e -> "Ubo" + e.name + "Params")
                .toArray(String[]::new);
    }

    /**
     * Returns the next available binding index (one past the highest registered).
     */
    public int nextBinding() {
        return usedBindings.stream().mapToInt(Integer::intValue).max().orElse(-1) + 1;
    }

    /**
     * A registered global param block.
     */
    public record Entry(String name, Class<?> recordType, int binding, Object data) {}
}
