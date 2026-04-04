package dev.engine.graphics.shader;

import dev.engine.core.math.*;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generates Slang struct source code from Java records.
 *
 * <p>Single source of truth: the Java record defines both the CPU-side
 * memory layout (via StructLayout) AND the GPU-side shader struct.
 *
 * <p>Supports primitives (float, int, double), vector types (Vec2, Vec3, Vec4),
 * matrices (Mat4), and nested records (generates dependent structs).
 */
public final class SlangStructGenerator {

    private static final Map<Class<?>, String> TYPE_MAP = new LinkedHashMap<>();

    static {
        TYPE_MAP.put(float.class, "float");
        TYPE_MAP.put(Float.class, "float");
        TYPE_MAP.put(int.class, "int");
        TYPE_MAP.put(Integer.class, "int");
        TYPE_MAP.put(double.class, "double");
        TYPE_MAP.put(Double.class, "double");
        TYPE_MAP.put(long.class, "int64_t");
        TYPE_MAP.put(Long.class, "int64_t");
        TYPE_MAP.put(boolean.class, "bool");
        TYPE_MAP.put(Boolean.class, "bool");
        TYPE_MAP.put(short.class, "int16_t");
        TYPE_MAP.put(Short.class, "int16_t");
        TYPE_MAP.put(byte.class, "uint8_t");
        TYPE_MAP.put(Byte.class, "uint8_t");
        TYPE_MAP.put(Vec2.class, "float2");
        TYPE_MAP.put(Vec3.class, "float3");
        TYPE_MAP.put(Vec4.class, "float4");
        TYPE_MAP.put(Mat4.class, "float4x4");
    }

    private SlangStructGenerator() {}

    /**
     * Generates a single Slang struct from a Java record.
     */
    public static String generate(Class<?> recordType) {
        if (!recordType.isRecord()) {
            throw new IllegalArgumentException(recordType.getName() + " is not a record");
        }
        var sb = new StringBuilder();
        sb.append("struct ").append(recordType.getSimpleName()).append(" {\n");
        for (var comp : recordType.getRecordComponents()) {
            sb.append("    ").append(mapType(comp)).append(" ").append(comp.getName()).append(";\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * Generates Slang structs for a record and all its record dependencies,
     * in dependency order (dependencies first).
     */
    public static String generateWithDependencies(Class<?> recordType) {
        var ordered = new LinkedHashSet<Class<?>>();
        collectDependencies(recordType, ordered);

        var sb = new StringBuilder();
        for (var type : ordered) {
            sb.append(generate(type)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Generates a Slang cbuffer declaration from a record, inlining all fields.
     */
    public static String generateCbuffer(String name, Class<?> recordType, int binding) {
        if (!recordType.isRecord()) {
            throw new IllegalArgumentException(recordType.getName() + " is not a record");
        }
        var sb = new StringBuilder();
        sb.append("cbuffer ").append(name).append(" {\n");
        for (var comp : recordType.getRecordComponents()) {
            sb.append("    ").append(mapType(comp)).append(" ").append(comp.getName()).append(";\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * Registers a custom type mapping (for user-defined types).
     */
    public static void registerType(Class<?> javaType, String slangType) {
        TYPE_MAP.put(javaType, slangType);
    }

    private static String mapType(RecordComponent comp) {
        var type = comp.getType();
        var mapped = TYPE_MAP.get(type);
        if (mapped != null) return mapped;

        // Nested record → reference by record name
        if (type.isRecord()) return type.getSimpleName();

        throw new IllegalArgumentException("Unsupported type: " + type.getName()
                + " for field " + comp.getName()
                + ". Register it with SlangStructGenerator.registerType()");
    }

    private static void collectDependencies(Class<?> type, Set<Class<?>> ordered) {
        if (!type.isRecord() || ordered.contains(type)) return;

        // Process dependencies first
        for (var comp : type.getRecordComponents()) {
            var compType = comp.getType();
            if (compType.isRecord() && !TYPE_MAP.containsKey(compType)) {
                collectDependencies(compType, ordered);
            }
        }
        ordered.add(type);
    }
}
