package dev.engine.graphics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registry of device capabilities. Backends register suppliers at init time.
 * Users can register custom capabilities without modifying engine code.
 *
 * <p>Adding a new capability is just:
 * <pre>
 *   var MY_CAP = DeviceCapability.intCap("MY_CUSTOM_LIMIT");
 *   registry.register(MY_CAP, () -> glGetInteger(GL_MY_LIMIT));
 * </pre>
 */
public class CapabilityRegistry {

    private final Map<DeviceCapability<?>, Supplier<?>> suppliers = new ConcurrentHashMap<>();

    public <T> void register(DeviceCapability<T> capability, Supplier<T> supplier) {
        suppliers.put(capability, supplier);
    }

    public <T> void registerStatic(DeviceCapability<T> capability, T value) {
        suppliers.put(capability, () -> value);
    }

    @SuppressWarnings("unchecked")
    public <T> T query(DeviceCapability<T> capability) {
        var supplier = suppliers.get(capability);
        if (supplier == null) return null;
        return (T) supplier.get();
    }

    public boolean supports(DeviceCapability<Boolean> feature) {
        Boolean result = query(feature);
        return result != null && result;
    }

    public boolean has(DeviceCapability<?> capability) {
        return suppliers.containsKey(capability);
    }
}
