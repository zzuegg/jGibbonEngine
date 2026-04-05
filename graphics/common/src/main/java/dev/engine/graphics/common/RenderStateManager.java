package dev.engine.graphics.common;

import dev.engine.core.material.MaterialData;
import dev.engine.core.property.MutablePropertyMap;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.graphics.renderstate.RenderState;

/**
 * Three-layer render state resolution: defaults &lt; material &lt; forced.
 *
 * <p>Material render state overrides are read from the nested
 * {@link MaterialData#RENDER_STATE} property map.
 */
public class RenderStateManager {

    private final MutablePropertyMap<RenderState> defaultProperties = new MutablePropertyMap<>();
    private final MutablePropertyMap<RenderState> forcedProperties = new MutablePropertyMap<>();

    public RenderStateManager() {
        PropertyMap<RenderState> defaults = RenderState.defaults();
        for (var key : defaults.keys()) {
            @SuppressWarnings("unchecked")
            var typedKey = (PropertyKey<RenderState, Object>) key;
            defaultProperties.set(typedKey, defaults.get(typedKey));
        }
    }

    public <T> void setDefault(PropertyKey<RenderState, T> key, T value) {
        defaultProperties.set(key, value);
    }

    public <T> void forceProperty(PropertyKey<RenderState, T> key, T value) {
        forcedProperties.set(key, value);
    }

    public <T> void clearForced(PropertyKey<RenderState, T> key) {
        forcedProperties.remove(key);
    }

    @SuppressWarnings("unchecked")
    public PropertyMap<RenderState> resolve(MaterialData material) {
        var builder = PropertyMap.<RenderState>builder();

        // Layer 1: defaults
        for (var key : defaultProperties.keys()) {
            var typedKey = (PropertyKey<RenderState, Object>) key;
            builder.set(typedKey, defaultProperties.get(typedKey));
        }

        // Layer 2: material overrides (from nested render state map)
        if (material != null) {
            PropertyMap<RenderState> matRenderState = material.renderState();
            if (matRenderState != null) {
                for (var key : matRenderState.keys()) {
                    var typedKey = (PropertyKey<RenderState, Object>) key;
                    builder.set(typedKey, matRenderState.get(typedKey));
                }
            }
        }

        // Layer 3: forced overrides
        for (var key : forcedProperties.keys()) {
            var typedKey = (PropertyKey<RenderState, Object>) key;
            builder.set(typedKey, forcedProperties.get(typedKey));
        }

        return builder.build();
    }

    /**
     * Returns true if the given key is a standard render state key.
     * Used by shader/uniform managers to filter render state keys from material keys.
     */
    public static boolean isRenderStateKey(PropertyKey<?, ?> key) {
        return key == RenderState.DEPTH_TEST || key == RenderState.DEPTH_WRITE
            || key == RenderState.DEPTH_FUNC || key == RenderState.BLEND_MODE
            || key == RenderState.BLEND_MODES
            || key == RenderState.CULL_MODE || key == RenderState.FRONT_FACE
            || key == RenderState.WIREFRAME || key == RenderState.LINE_WIDTH
            || key == RenderState.SCISSOR_TEST
            || key == RenderState.STENCIL_TEST || key == RenderState.STENCIL_FUNC
            || key == RenderState.STENCIL_REF || key == RenderState.STENCIL_MASK
            || key == RenderState.STENCIL_FAIL || key == RenderState.STENCIL_DEPTH_FAIL
            || key == RenderState.STENCIL_PASS
            || key == MaterialData.RENDER_STATE;
    }
}
