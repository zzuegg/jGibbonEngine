package dev.engine.bindings.slang;

import dev.engine.core.math.*;
import dev.engine.graphics.shader.SlangParamsBlock;

import java.util.ArrayList;
import java.util.Map;

/**
 * Reflection-based helper for building {@link SlangParamsBlock} from Java records.
 * Uses {@code Class.isRecord()} and {@code Class.getRecordComponents()} which
 * are NOT available on TeaVM.
 *
 * <p>This class is loaded dynamically via {@code Class.forName()} only when
 * {@link dev.engine.core.layout.RecordRegistry} has no metadata for the type.
 * On TeaVM, this class is never loaded.
 */
public final class ReflectiveSlangHelper {

    private static final Map<Class<?>, String> TYPE_MAP = Map.ofEntries(
            Map.entry(Float.class, "float"), Map.entry(float.class, "float"),
            Map.entry(Integer.class, "int"), Map.entry(int.class, "int"),
            Map.entry(Boolean.class, "bool"), Map.entry(boolean.class, "bool"),
            Map.entry(Double.class, "double"), Map.entry(double.class, "double"),
            Map.entry(Vec2.class, "float2"), Map.entry(Vec3.class, "float3"),
            Map.entry(Vec4.class, "float4"), Map.entry(Mat4.class, "float4x4")
    );

    private ReflectiveSlangHelper() {}

    /**
     * Creates a {@link SlangParamsBlock} from a Java record using native reflection.
     * Called reflectively by {@link SlangParamsBlock#fromRecord}.
     */
    public static SlangParamsBlock fromRecord(String name, Class<?> recordType) {
        if (!recordType.isRecord()) {
            throw new IllegalArgumentException(recordType.getName() + " is not a record");
        }
        var components = recordType.getRecordComponents();
        // Use the package-private factory to build the block
        var fields = new ArrayList<SlangParamsBlock.FieldEntry>();
        for (var comp : components) {
            var slangType = TYPE_MAP.get(comp.getType());
            if (slangType == null) {
                throw new IllegalArgumentException("Unsupported type: " + comp.getType().getName()
                        + " for field " + comp.getName());
            }
            fields.add(new SlangParamsBlock.FieldEntry(comp.getName(), slangType));
        }
        return SlangParamsBlock.createFromEntries(name, fields);
    }
}
