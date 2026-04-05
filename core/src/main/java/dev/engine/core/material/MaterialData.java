package dev.engine.core.material;

import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.math.Vec4;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.core.scene.Component;

import java.util.Set;

/**
 * Immutable material data — a typed property map that is a component.
 *
 * <p>The shader always accesses material data through a generated Slang interface.
 * The engine generates the appropriate implementation (UBO, SSBO, bindless)
 * based on the upload strategy. Shader code never changes.
 *
 * <p>Render state overrides are stored in a nested {@code PropertyMap<RenderState>}
 * under the {@link #RENDER_STATE} key, keeping material and render state concerns separated.
 *
 * <pre>
 * var mat = MaterialData.create()
 *     .set(MaterialData.ALBEDO_COLOR, new Vec3(1, 0, 0))
 *     .set(MaterialData.ROUGHNESS, 0.5f)
 *     .withRenderState(RenderState.CULL_MODE, CullMode.FRONT);
 * entity.add(mat);
 * </pre>
 */
public final class MaterialData implements Component {

    // --- Standard PBR keys ---
    public static final PropertyKey<MaterialData, Vec3> ALBEDO_COLOR = PropertyKey.of("albedoColor", Vec3.class);
    public static final PropertyKey<MaterialData, Float> ROUGHNESS = PropertyKey.of("roughness", Float.class);
    public static final PropertyKey<MaterialData, Float> METALLIC = PropertyKey.of("metallic", Float.class);
    public static final PropertyKey<MaterialData, Vec3> EMISSIVE = PropertyKey.of("emissive", Vec3.class);
    public static final PropertyKey<MaterialData, Float> OPACITY = PropertyKey.of("opacity", Float.class);
    public static final PropertyKey<MaterialData, Float> NORMAL_STRENGTH = PropertyKey.of("normalStrength", Float.class);

    // Texture keys are in dev.engine.graphics.texture.TextureKeys (graphics:api)
    // to avoid core depending on graphics types.

    // --- Unlit keys ---
    public static final PropertyKey<MaterialData, Vec3> COLOR = PropertyKey.of("color", Vec3.class);

    // --- Render state override (nested property map) ---
    @SuppressWarnings("unchecked")
    public static final PropertyKey<MaterialData, PropertyMap<?>> RENDER_STATE =
            (PropertyKey<MaterialData, PropertyMap<?>>) (PropertyKey<?, ?>) PropertyKey.of("renderState", PropertyMap.class);

    private final PropertyMap<MaterialData> properties;
    private final String shaderHint;

    private MaterialData(PropertyMap<MaterialData> properties, String shaderHint) {
        this.properties = properties;
        this.shaderHint = shaderHint;
    }

    /** Creates an empty material. */
    public static MaterialData create() {
        return new MaterialData(PropertyMap.<MaterialData>builder().build(), null);
    }

    /** Creates with an explicit shader hint (e.g. "PBR", "UNLIT", "custom/toon.slang"). */
    public static MaterialData create(String shaderHint) {
        return new MaterialData(PropertyMap.<MaterialData>builder().build(), shaderHint);
    }

    // --- Immutable setters (return new MaterialData) ---

    public <T> MaterialData set(PropertyKey<MaterialData, T> key, T value) {
        var builder = PropertyMap.<MaterialData>builder();
        for (var k : properties.keys()) {
            @SuppressWarnings("unchecked")
            var typedKey = (PropertyKey<MaterialData, Object>) k;
            builder.set(typedKey, properties.get(typedKey));
        }
        builder.set(key, value);
        return new MaterialData(builder.build(), shaderHint);
    }

    /**
     * Adds a render state override to this material. Render state keys are stored
     * in a nested PropertyMap under {@link #RENDER_STATE}.
     */
    @SuppressWarnings("unchecked")
    public <R, T> MaterialData withRenderState(PropertyKey<R, T> key, T value) {
        PropertyMap<?> existing = properties.get(RENDER_STATE);
        var rsBuilder = PropertyMap.<Object>builder();
        if (existing != null) {
            var typedExisting = (PropertyMap<Object>) existing;
            for (var k : typedExisting.keys()) {
                var typedKey = (PropertyKey<Object, Object>) k;
                rsBuilder.set(typedKey, typedExisting.get(typedKey));
            }
        }
        rsBuilder.set((PropertyKey<Object, Object>) (PropertyKey<?, ?>) key, value);
        return set(RENDER_STATE, rsBuilder.build());
    }

    /**
     * Returns the render state overrides for this material, or null if none.
     */
    @SuppressWarnings("unchecked")
    public <R> PropertyMap<R> renderState() {
        return (PropertyMap<R>) properties.get(RENDER_STATE);
    }

    public MaterialData withShader(String hint) {
        return new MaterialData(properties, hint);
    }

    // --- Getters ---

    public <T> T get(PropertyKey<MaterialData, T> key) { return properties.get(key); }
    public boolean has(PropertyKey<MaterialData, ?> key) { return properties.contains(key); }
    public Set<PropertyKey<MaterialData, ?>> keys() { return properties.keys(); }
    public int size() { return properties.size(); }
    public PropertyMap<MaterialData> properties() { return properties; }
    public String shaderHint() { return shaderHint; }

    // --- Convenience factories ---

    public static MaterialData pbr(Vec3 albedo, float roughness, float metallic) {
        return create("PBR")
                .set(ALBEDO_COLOR, albedo)
                .set(ROUGHNESS, roughness)
                .set(METALLIC, metallic)
                .set(EMISSIVE, Vec3.ZERO)
                .set(OPACITY, 1f);
    }

    public static MaterialData unlit(Vec3 color) {
        return create("UNLIT").set(COLOR, color);
    }

    /** One slot per entity — replaces any existing material. */
    @Override
    public Class<? extends Component> slotType() { return MaterialData.class; }

    @Override
    public String toString() {
        return "MaterialData{keys=" + properties.keys().stream().map(PropertyKey::name).toList() + ", shader=" + shaderHint + "}";
    }
}
