package dev.engine.graphics.common;

import dev.engine.core.material.MaterialData;
import dev.engine.core.property.MutablePropertyMap;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.graphics.renderstate.RenderState;

/**
 * Three-layer render state resolution: defaults &lt; material &lt; forced.
 */
public class RenderStateManager {

    private final MutablePropertyMap defaultProperties = new MutablePropertyMap();
    private final MutablePropertyMap forcedProperties = new MutablePropertyMap();

    public RenderStateManager() {
        PropertyMap defaults = RenderState.defaults();
        for (var key : defaults.keys()) {
            @SuppressWarnings("unchecked")
            var typedKey = (PropertyKey<Object>) key;
            defaultProperties.set(typedKey, defaults.get(key));
        }
    }

    public <T> void setDefault(PropertyKey<T> key, T value) {
        defaultProperties.set(key, value);
    }

    public <T> void forceProperty(PropertyKey<T> key, T value) {
        forcedProperties.set(key, value);
    }

    public <T> void clearForced(PropertyKey<T> key) {
        forcedProperties.remove(key);
    }

    @SuppressWarnings("unchecked")
    public PropertyMap resolve(MaterialData material) {
        var builder = PropertyMap.builder();
        for (var key : defaultProperties.keys()) {
            builder.set((PropertyKey<Object>) key, defaultProperties.get(key));
        }
        if (material != null) {
            for (var key : material.keys()) {
                Object value = material.get(key);
                if (value != null && isRenderStateKey(key)) {
                    builder.set((PropertyKey<Object>) key, value);
                }
            }
        }
        for (var key : forcedProperties.keys()) {
            builder.set((PropertyKey<Object>) key, forcedProperties.get(key));
        }
        return builder.build();
    }

    public static boolean isRenderStateKey(PropertyKey<?> key) {
        return key == RenderState.DEPTH_TEST || key == RenderState.DEPTH_WRITE
            || key == RenderState.DEPTH_FUNC || key == RenderState.BLEND_MODE
            || key == RenderState.CULL_MODE || key == RenderState.FRONT_FACE
            || key == RenderState.WIREFRAME || key == RenderState.LINE_WIDTH
            || key == RenderState.SCISSOR_TEST
            || key == RenderState.STENCIL_TEST || key == RenderState.STENCIL_FUNC
            || key == RenderState.STENCIL_REF || key == RenderState.STENCIL_MASK
            || key == RenderState.STENCIL_FAIL || key == RenderState.STENCIL_DEPTH_FAIL
            || key == RenderState.STENCIL_PASS;
    }
}
