package dev.engine.core.layout;

import dev.engine.core.memory.NativeMemory;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class StructLayout {

    public record Field(String name, Class<?> type, int offset, int size, int alignment) {}

    private static final Map<String, StructLayout> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private final List<Field> fields;
    private final int size;
    private final List<FieldWriter> writers;
    private final Class<?> recordType;
    private final LayoutMode mode;

    private StructLayout(Class<?> recordType, LayoutMode mode, List<Field> fields, int size, List<FieldWriter> writers) {
        this.recordType = recordType;
        this.mode = mode;
        this.fields = Collections.unmodifiableList(fields);
        this.size = size;
        this.writers = writers;
    }

    /** Packed layout (vertex buffers, CPU-side). */
    public static synchronized StructLayout of(Class<?> recordType) {
        return of(recordType, LayoutMode.PACKED);
    }

    /** Layout with specific mode (PACKED, STD140, STD430). */
    public static synchronized StructLayout of(Class<?> recordType, LayoutMode mode) {
        var key = recordType.getName() + ":" + mode.name();
        var cached = CACHE.get(key);
        if (cached != null) return cached;

        // Try loading generated metadata class (triggers static registration in RecordRegistry + CACHE)
        try {
            Class.forName(recordType.getName() + "_NativeStruct");
            cached = CACHE.get(key);
            if (cached != null) return cached;
        } catch (ClassNotFoundException ignored) {}

        // Fallback: try reflection-based builder (desktop only, not available on TeaVM)
        try {
            var builderClass = Class.forName("dev.engine.core.layout.ReflectiveLayoutBuilder");
            var buildMethod = builderClass.getMethod("build", Class.class, LayoutMode.class);
            var layout = (StructLayout) buildMethod.invoke(null, recordType, mode);
            CACHE.put(key, layout);
            return layout;
        } catch (Exception e) {
            // ReflectiveLayoutBuilder not available or failed (e.g., TeaVM has no MethodHandles)
        }

        throw new IllegalStateException(
            "No layout for " + recordType.getName() +
            ". Add @NativeStruct annotation to generate it at compile time.");
    }

    /**
     * Pre-registers a struct layout, bypassing reflection.
     * Use this for platforms that don't support Class.getRecordComponents() (e.g., TeaVM).
     * Must be called before StructLayout.of() for the same type.
     */
    public static synchronized void register(Class<?> recordType, LayoutMode mode, StructLayout layout) {
        var key = recordType.getName() + ":" + mode.name();
        CACHE.put(key, layout);
    }

    /**
     * Creates a StructLayout from manually specified fields (no reflection needed).
     * The write function receives (memory, baseOffset, record) and must write
     * all fields relative to the given base offset.
     */
    public static StructLayout manual(Class<?> recordType, LayoutMode mode, List<Field> fields, int size,
                                       WriteFunction writeFunc) {
        var writers = List.of((FieldWriter) (mem, offset, record) -> writeFunc.write(mem, offset, record));
        return new StructLayout(recordType, mode, fields, size, writers);
    }

    /**
     * A write function that serializes a record to native memory at a given offset.
     */
    @FunctionalInterface
    public interface WriteFunction {
        void write(NativeMemory memory, long baseOffset, Object record);
    }

    public List<Field> fields() { return fields; }
    public int size() { return size; }
    public LayoutMode mode() { return mode; }

    public void write(NativeMemory memory, long offset, Object record) {
        for (var writer : writers) {
            writer.write(memory, offset, record);
        }
    }

    // Layout computation is done at compile time by the @NativeStruct annotation processor.
    // On desktop, unregistered types fall back to ReflectiveLayoutBuilder via Class.forName().

    @FunctionalInterface
    private interface FieldWriter {
        void write(NativeMemory memory, long baseOffset, Object record);
    }
}
