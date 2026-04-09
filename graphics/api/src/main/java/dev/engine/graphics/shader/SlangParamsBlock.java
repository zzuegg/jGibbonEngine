package dev.engine.graphics.shader;

import dev.engine.core.asset.TextureData;
import dev.engine.core.layout.RecordRegistry;
import dev.engine.core.math.*;
import dev.engine.core.property.PropertyKey;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates Slang interface + implementation blocks for shader parameter access.
 *
 * <p>Two creation modes:
 * <ul>
 *   <li>{@link #fromKeys} — dynamic params from PropertyKeys (materials).
 *       Generates an interface for generic specialization: shaders declare
 *       {@code <M : IMaterialParams>} and the concrete type is injected.</li>
 *   <li>{@link #fromRecord} — static params from a Java record (camera, engine).
 *       Generates interface + implementation + a static global instance
 *       (e.g., {@code camera.get_viewProjection()}).</li>
 * </ul>
 *
 * <p>The upload strategy (UBO or SSBO) determines the generated implementation.
 */
public final class SlangParamsBlock {

    private static final Map<Class<?>, String> TYPE_MAP = Map.ofEntries(
            Map.entry(Float.class, "float"), Map.entry(float.class, "float"),
            Map.entry(Integer.class, "int"), Map.entry(int.class, "int"),
            Map.entry(Boolean.class, "bool"), Map.entry(boolean.class, "bool"),
            Map.entry(Double.class, "double"), Map.entry(double.class, "double"),
            Map.entry(Vec2.class, "float2"), Map.entry(Vec3.class, "float3"),
            Map.entry(Vec4.class, "float4"), Map.entry(Mat4.class, "float4x4")
    );

    private final String name; // e.g. "Material", "Camera", "Engine"
    private final List<Field> fields;
    private final int binding; // -1 = no explicit binding

    private SlangParamsBlock(String name, List<Field> fields, int binding) {
        this.name = name;
        this.fields = fields;
        this.binding = binding;
    }

    /**
     * Returns a copy with an explicit binding index.
     * The generated cbuffer will include {@code register(bN)}.
     */
    public SlangParamsBlock withBinding(int bindingIndex) {
        return new SlangParamsBlock(name, fields, bindingIndex);
    }

    /**
     * Creates a params block from PropertyKeys (for materials).
     * Generates interface + implementation + static global instance.
     */
    public static SlangParamsBlock fromKeys(String name, Set<? extends PropertyKey<?, ?>> keys) {
        var fields = keys.stream()
                .filter(k -> TYPE_MAP.containsKey(k.type()))
                .sorted(Comparator.comparing(PropertyKey::name))
                .map(k -> new Field(k.name(), TYPE_MAP.get(k.type())))
                .toList();
        return new SlangParamsBlock(name, fields, -1);
    }

    /**
     * Creates a params block from a Java record (for camera, engine, etc.).
     * Generates interface + implementation + static global instance.
     *
     * <p>Uses {@link RecordRegistry} first (populated by {@code @NativeStruct} processor),
     * falling back to native reflection for unregistered types (desktop only).
     */
    public static SlangParamsBlock fromRecord(String name, Class<?> recordType) {
        // Ensure all @Discoverable/@NativeStruct registries are initialized
        dev.engine.core.Discovery.ensureInitialized();

        // Check RecordRegistry first (works on all platforms)
        var components = RecordRegistry.getComponents(recordType);
        if (components != null) {
            var fields = new ArrayList<Field>();
            for (var comp : components) {
                var slangType = TYPE_MAP.get(comp.type());
                if (slangType == null) {
                    throw new IllegalArgumentException("Unsupported type: " + comp.type().getName()
                            + " for field " + comp.name());
                }
                fields.add(new Field(comp.name(), slangType));
            }
            return new SlangParamsBlock(name, fields, -1);
        }

        // Fallback: native reflection (desktop only, not available on TeaVM)
        try {
            var helperClass = Class.forName("dev.engine.bindings.slang.ReflectiveSlangHelper");
            var method = helperClass.getMethod("fromRecord", String.class, Class.class);
            return (SlangParamsBlock) method.invoke(null, name, recordType);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("No metadata for " + recordType.getName()
                    + ". Add @NativeStruct annotation for cross-platform compatibility.");
        } catch (java.lang.reflect.InvocationTargetException e) {
            var cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            if (cause instanceof IllegalArgumentException iae) throw iae;
            throw new RuntimeException("Failed to build SlangParamsBlock for " + recordType.getName(), cause);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build SlangParamsBlock for " + recordType.getName(), e);
        }
    }

    /** Generates the full UBO-backed block with a static global instance. */
    public String generateUbo() {
        return generateUbo(true);
    }

    /**
     * Generates the full UBO-backed block (interface + struct + cbuffer + impl).
     * @param includeGlobal if true, adds a static global instance (e.g., {@code static UboMaterialParams material;}).
     *                      Set to false when using generic specialization (the shader creates the instance via {@code M material;}).
     */
    public String generateUbo(boolean includeGlobal) {
        var sb = new StringBuilder();
        sb.append(generateInterface());
        sb.append("\n");

        if (fields.isEmpty()) {
            // No scalar fields — emit a minimal impl with no cbuffer.
            // This happens when the material only has texture keys (e.g., albedoTexture).
            sb.append("struct Ubo").append(name).append("Params : I").append(name).append("Params {\n};\n");
        } else {
            sb.append(generateDataStruct());
            sb.append("\n");
            sb.append(generateUboCbuffer());
            sb.append("\n");
            sb.append(generateUboImpl());
        }

        if (includeGlobal) {
            sb.append("\n");
            sb.append(generateGlobal("Ubo"));
        }
        return sb.toString();
    }

    /** Generates the full SSBO-backed block with a static global instance. */
    public String generateSsbo() {
        return generateSsbo(true);
    }

    /**
     * Generates the full SSBO-backed block (interface + struct + buffer + impl).
     * @param includeGlobal if true, adds a static global instance.
     */
    public String generateSsbo(boolean includeGlobal) {
        var sb = new StringBuilder();
        sb.append(generateInterface());
        sb.append("\n");
        sb.append(generateDataStruct());
        sb.append("\n");
        sb.append(generateSsboBuffer());
        sb.append("\n");
        sb.append(generateSsboImpl());
        if (includeGlobal) {
            sb.append("\n");
            sb.append(generateGlobal("Ssbo"));
        }
        return sb.toString();
    }

    /** The interface name, e.g. "IMaterialParams". */
    public String interfaceName() {
        return "I" + name + "Params";
    }

    /** The concrete UBO type name, e.g. "UboMaterialParams". */
    public String uboTypeName() {
        return "Ubo" + name + "Params";
    }

    /** The concrete SSBO type name, e.g. "SsboMaterialParams". */
    public String ssboTypeName() {
        return "Ssbo" + name + "Params";
    }

    // --- Internal generation ---

    private String generateInterface() {
        var sb = new StringBuilder();
        sb.append("interface I").append(name).append("Params {\n");
        for (var field : fields) {
            sb.append("    ").append(field.slangType).append(" ").append(field.name).append("();\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    private String generateDataStruct() {
        var sb = new StringBuilder();
        sb.append("struct ").append(name).append("ParamsData {\n");
        for (var field : fields) {
            sb.append("    ").append(field.slangType).append(" ").append(field.name).append(";\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    private String generateUboCbuffer() {
        var sb = new StringBuilder();
        if (binding >= 0) {
            // Vulkan binding annotation (must come before the cbuffer declaration)
            sb.append("[[vk::binding(").append(binding).append(")]]\n");
        }
        sb.append("cbuffer ").append(name).append("Buffer");
        if (binding >= 0) {
            sb.append(" : register(b").append(binding).append(")");
        }
        sb.append(" {\n");
        sb.append("    ").append(name).append("ParamsData ").append(dataVarName()).append(";\n");
        sb.append("};\n");
        return sb.toString();
    }

    private String generateUboImpl() {
        var sb = new StringBuilder();
        sb.append("struct Ubo").append(name).append("Params : I").append(name).append("Params {\n");
        for (var field : fields) {
            sb.append("    ").append(field.slangType).append(" ").append(field.name)
                    .append("() { return ").append(dataVarName()).append(".").append(field.name).append("; }\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    private String generateSsboBuffer() {
        var sb = new StringBuilder();
        sb.append("StructuredBuffer<").append(name).append("ParamsData> ").append(arrayVarName()).append(";\n");
        return sb.toString();
    }

    private String generateSsboImpl() {
        var sb = new StringBuilder();
        sb.append("struct Ssbo").append(name).append("Params : I").append(name).append("Params {\n");
        sb.append("    uint instanceId;\n");
        for (var field : fields) {
            sb.append("    ").append(field.slangType).append(" ").append(field.name)
                    .append("() { return ").append(arrayVarName()).append("[instanceId].")
                    .append(field.name).append("; }\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    private String generateGlobal(String prefix) {
        var instanceName = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        return "static " + prefix + name + "Params " + instanceName + ";\n";
    }

    private String dataVarName() {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1) + "Data";
    }

    private String arrayVarName() {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1) + "Array";
    }

    private record Field(String name, String slangType) {}

    /** Entry type for reflective construction. */
    public record FieldEntry(String name, String slangType) {}

    /** Factory for {@link ReflectiveSlangHelper}. */
    public static SlangParamsBlock createFromEntries(String name, java.util.List<FieldEntry> entries) {
        var fields = entries.stream().map(e -> new Field(e.name, e.slangType)).toList();
        return new SlangParamsBlock(name, fields, -1);
    }
}
