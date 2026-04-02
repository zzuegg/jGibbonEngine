package dev.engine.graphics.common.material;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;
import dev.engine.core.property.PropertyKey;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Compiles material properties into shader code and GPU-uploadable data.
 *
 * <p>This bridges the gap between the user's property bag (Material) and
 * the GPU's typed buffers + shader structs. The user never writes shader code
 * for standard materials — this generates everything automatically.
 */
public final class MaterialCompiler {

    private static final Map<Class<?>, String> SLANG_TYPES = new LinkedHashMap<>();
    private static final Map<Class<?>, Integer> TYPE_SIZES = new LinkedHashMap<>();

    static {
        SLANG_TYPES.put(Float.class, "float");
        SLANG_TYPES.put(float.class, "float");
        SLANG_TYPES.put(Integer.class, "int");
        SLANG_TYPES.put(int.class, "int");
        SLANG_TYPES.put(Boolean.class, "bool");
        SLANG_TYPES.put(boolean.class, "bool");
        SLANG_TYPES.put(Vec2.class, "float2");
        SLANG_TYPES.put(Vec3.class, "float3");
        SLANG_TYPES.put(Vec4.class, "float4");
        SLANG_TYPES.put(Mat4.class, "float4x4");

        TYPE_SIZES.put(Float.class, 4);
        TYPE_SIZES.put(float.class, 4);
        TYPE_SIZES.put(Integer.class, 4);
        TYPE_SIZES.put(int.class, 4);
        TYPE_SIZES.put(Boolean.class, 4); // std430 bool = 4 bytes
        TYPE_SIZES.put(boolean.class, 4);
        TYPE_SIZES.put(Vec2.class, 8);
        TYPE_SIZES.put(Vec3.class, 12);
        TYPE_SIZES.put(Vec4.class, 16);
        TYPE_SIZES.put(Mat4.class, 64);
    }

    private MaterialCompiler() {}

    /**
     * Derives a shader key from a material's type + property set.
     * Same key = same shader variant. Different properties = different key.
     */
    public static String shaderKey(Material mat) {
        var propNames = getPropertyList(mat).stream()
                .map(e -> e.getKey().name())
                .sorted()
                .collect(Collectors.joining("_"));
        if (propNames.isEmpty()) return mat.type().name();
        return mat.type().name() + "_" + propNames;
    }

    /**
     * Generates a Slang struct definition from the material's actual properties.
     */
    public static String generateMaterialStruct(Material mat) {
        var sb = new StringBuilder();
        sb.append("struct MaterialData {\n");
        for (var entry : getPropertyList(mat)) {
            var slangType = SLANG_TYPES.get(entry.getKey().type());
            if (slangType == null) continue;
            sb.append("    ").append(slangType).append(" ").append(entry.getKey().name()).append(";\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * Generates a Slang cbuffer with the material's properties inlined.
     */
    public static String generateMaterialCbuffer(Material mat, int binding) {
        var sb = new StringBuilder();
        sb.append("cbuffer MaterialData : register(b").append(binding).append(") {\n");
        for (var entry : getPropertyList(mat)) {
            var slangType = SLANG_TYPES.get(entry.getKey().type());
            if (slangType == null) continue;
            sb.append("    ").append(slangType).append(" ").append(entry.getKey().name()).append(";\n");
        }
        sb.append("};\n");
        return sb.toString();
    }

    /**
     * Serializes the material's property values into a ByteBuffer for GPU upload.
     * Properties are laid out in the same order as the generated struct.
     */
    public static ByteBuffer serializeMaterialData(Material mat) {
        var props = getPropertyList(mat);
        int totalSize = 0;
        for (var entry : props) {
            var size = TYPE_SIZES.getOrDefault(entry.getKey().type(), 0);
            // Align Vec3 to 16 bytes in std140/std430
            if (entry.getKey().type() == Vec3.class) {
                totalSize = align(totalSize, 16);
                totalSize += 16; // Vec3 takes 16 bytes with padding
            } else {
                totalSize += size;
            }
        }

        var buf = ByteBuffer.allocateDirect(Math.max(totalSize, 16)).order(ByteOrder.nativeOrder());
        int offset = 0;
        for (var entry : props) {
            var value = entry.getValue();
            var type = entry.getKey().type();

            if (type == Float.class || type == float.class) {
                buf.putFloat(offset, (float) value);
                offset += 4;
            } else if (type == Integer.class || type == int.class) {
                buf.putInt(offset, (int) value);
                offset += 4;
            } else if (type == Boolean.class || type == boolean.class) {
                buf.putInt(offset, ((boolean) value) ? 1 : 0);
                offset += 4;
            } else if (type == Vec2.class) {
                var v = (Vec2) value;
                buf.putFloat(offset, v.x()); buf.putFloat(offset + 4, v.y());
                offset += 8;
            } else if (type == Vec3.class) {
                offset = align(offset, 16);
                var v = (Vec3) value;
                buf.putFloat(offset, v.x()); buf.putFloat(offset + 4, v.y()); buf.putFloat(offset + 8, v.z());
                offset += 16; // padded
            } else if (type == Vec4.class) {
                var v = (Vec4) value;
                buf.putFloat(offset, v.x()); buf.putFloat(offset + 4, v.y());
                buf.putFloat(offset + 8, v.z()); buf.putFloat(offset + 12, v.w());
                offset += 16;
            } else if (type == Mat4.class) {
                var m = (Mat4) value;
                // Write all 16 floats
                float[] vals = {m.m00(),m.m01(),m.m02(),m.m03(),m.m10(),m.m11(),m.m12(),m.m13(),
                        m.m20(),m.m21(),m.m22(),m.m23(),m.m30(),m.m31(),m.m32(),m.m33()};
                for (float v : vals) { buf.putFloat(offset, v); offset += 4; }
            }
        }
        buf.limit(offset);
        buf.position(0);
        return buf;
    }

    /**
     * Injects material struct/cbuffer into shader source, replacing the
     * {@code // __MATERIAL_DATA__} placeholder.
     */
    public static String injectMaterialData(String source, Material mat, int binding) {
        var cbuffer = generateMaterialCbuffer(mat, binding);
        return source.replace("// __MATERIAL_DATA__", cbuffer);
    }

    /**
     * Registers a custom type for material → Slang mapping.
     */
    public static void registerType(Class<?> javaType, String slangType, int sizeInBytes) {
        SLANG_TYPES.put(javaType, slangType);
        TYPE_SIZES.put(javaType, sizeInBytes);
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Map.Entry<PropertyKey<?>, Object>> getPropertyList(Material mat) {
        var result = new ArrayList<Map.Entry<PropertyKey<?>, Object>>();
        // Use reflection on the internal property map to get all set properties
        // For now, use the known standard keys and check which are set
        var knownKeys = new PropertyKey<?>[]{
                Material.ALBEDO_COLOR, Material.ROUGHNESS, Material.METALLIC,
                Material.EMISSIVE, Material.OPACITY, Material.COLOR
        };
        for (var key : knownKeys) {
            var value = mat.get(key);
            if (value != null) {
                result.add(Map.entry(key, value));
            }
        }
        // Sort by name for deterministic ordering
        result.sort(Comparator.comparing(e -> e.getKey().name()));
        return result;
    }

    private static int align(int offset, int alignment) {
        return (offset + alignment - 1) & ~(alignment - 1);
    }
}
