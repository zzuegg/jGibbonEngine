package dev.engine.core.layout;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registry of record metadata populated at compile time by the {@code @NativeStruct}
 * annotation processor. Each annotated record gets a generated {@code _NativeStruct}
 * class whose static initializer registers component info here.
 *
 * <p>This replaces {@code Class.isRecord()} and {@code Class.getRecordComponents()},
 * which are not available on TeaVM. Desktop code can also use this registry,
 * falling back to native reflection only for unregistered types.
 */
public final class RecordRegistry {

    /**
     * Describes one record component: its name, type, and an accessor function
     * that extracts the value from a record instance.
     */
    public record ComponentInfo(String name, Class<?> type, Function<Object, Object> accessor) {}

    private static final Map<Class<?>, ComponentInfo[]> REGISTRY = new ConcurrentHashMap<>();

    private RecordRegistry() {}

    /**
     * Registers record component metadata. Called from generated {@code _NativeStruct}
     * static initializers.
     */
    public static void register(Class<?> recordClass, ComponentInfo[] components) {
        REGISTRY.put(recordClass, components);
    }

    /**
     * Returns true if the given class has been registered (i.e., is a {@code @NativeStruct} record).
     */
    public static boolean isRegistered(Class<?> clazz) {
        return REGISTRY.containsKey(clazz);
    }

    /**
     * Returns the component metadata for a registered record, or null if not registered.
     */
    public static ComponentInfo[] getComponents(Class<?> clazz) {
        return REGISTRY.get(clazz);
    }
}
