package dev.engine.core.shader;

import dev.engine.core.asset.TextureData;
import dev.engine.core.math.*;
import dev.engine.core.property.PropertyKey;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates Slang interface + implementation for material parameters.
 *
 * <p>The shader always accesses data through {@code IMaterialParams}.
 * The implementation (UBO, SSBO array, bindless) is generated based on
 * the upload strategy and injected before compilation.
 */
public final class SlangMaterialGenerator {

    private static final Map<Class<?>, String> TYPE_MAP = Map.of(
            Float.class, "float", float.class, "float",
            Integer.class, "int", int.class, "int",
            Boolean.class, "bool", boolean.class, "bool",
            Vec2.class, "float2", Vec3.class, "float3",
            Vec4.class, "float4", Mat4.class, "float4x4"
    );

    private SlangMaterialGenerator() {}

    /**
     * Generates the Slang interface for material parameter access.
     * Each scalar/vector parameter becomes a getter method.
     * Textures are excluded from the interface — they're bound separately.
     */
    public static String generateInterface(Set<PropertyKey<?>> keys) {
        var scalarKeys = filterScalarKeys(keys);
        var sb = new StringBuilder();
        sb.append("interface IMaterialParams {\n");
        for (var key : scalarKeys) {
            var slangType = TYPE_MAP.get(key.type());
            if (slangType == null) continue;
            sb.append("    ").append(slangType).append(" ").append(key.name()).append("();\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * Generates a UBO-backed implementation of IMaterialParams.
     * Each parameter is a field in a cbuffer, accessor returns the field.
     */
    public static String generateUboImplementation(Set<PropertyKey<?>> keys) {
        var scalarKeys = filterScalarKeys(keys);
        var sb = new StringBuilder();

        // The data struct
        sb.append("struct MaterialParamsData {\n");
        for (var key : scalarKeys) {
            var slangType = TYPE_MAP.get(key.type());
            if (slangType == null) continue;
            sb.append("    ").append(slangType).append(" ").append(key.name()).append(";\n");
        }
        sb.append("};\n\n");

        // The cbuffer
        sb.append("cbuffer MaterialBuffer {\n");
        sb.append("    MaterialParamsData materialData;\n");
        sb.append("};\n\n");

        // The implementation
        sb.append("struct UboMaterialParams : IMaterialParams {\n");
        for (var key : scalarKeys) {
            var slangType = TYPE_MAP.get(key.type());
            if (slangType == null) continue;
            sb.append("    ").append(slangType).append(" ").append(key.name()).append("() { return materialData.").append(key.name()).append("; }\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * Generates an SSBO-backed implementation for instanced rendering.
     * All instance data in a StructuredBuffer, indexed by instance ID.
     */
    public static String generateSsboImplementation(Set<PropertyKey<?>> keys) {
        var scalarKeys = filterScalarKeys(keys);
        var sb = new StringBuilder();

        // The data struct (same as UBO)
        sb.append("struct MaterialParamsData {\n");
        for (var key : scalarKeys) {
            var slangType = TYPE_MAP.get(key.type());
            if (slangType == null) continue;
            sb.append("    ").append(slangType).append(" ").append(key.name()).append(";\n");
        }
        sb.append("};\n\n");

        // The SSBO
        sb.append("StructuredBuffer<MaterialParamsData> materialArray;\n\n");

        // The implementation
        sb.append("struct SsboMaterialParams : IMaterialParams {\n");
        sb.append("    uint instanceId;\n");
        for (var key : scalarKeys) {
            var slangType = TYPE_MAP.get(key.type());
            if (slangType == null) continue;
            sb.append("    ").append(slangType).append(" ").append(key.name()).append("() { return materialArray[instanceId].").append(key.name()).append("; }\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * Generates the full material injection block (interface + chosen implementation).
     */
    public static String generate(Set<PropertyKey<?>> keys, UploadMode mode) {
        var sb = new StringBuilder();
        sb.append(generateInterface(keys)).append("\n");
        switch (mode) {
            case UBO -> sb.append(generateUboImplementation(keys));
            case SSBO -> sb.append(generateSsboImplementation(keys));
        }
        return sb.toString();
    }

    /** Filters to only scalar/vector keys (no textures). */
    private static List<PropertyKey<?>> filterScalarKeys(Set<PropertyKey<?>> keys) {
        return keys.stream()
                .filter(k -> TYPE_MAP.containsKey(k.type()))
                .sorted(Comparator.comparing(PropertyKey::name))
                .toList();
    }

    public enum UploadMode { UBO, SSBO }
}
