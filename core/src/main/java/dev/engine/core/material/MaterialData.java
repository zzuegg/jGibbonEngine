package dev.engine.core.material;

import dev.engine.core.scene.Component;

/**
 * Base type for all material data. Entities have at most one MaterialData component.
 * Different material types (PBR, Unlit, Custom) implement this.
 *
 * <p>The renderer inspects the concrete type to select the shader and upload data.
 */
public interface MaterialData extends Component {

    /** All material types share one slot on an entity. */
    @Override
    default Class<? extends Component> slotType() { return MaterialData.class; }

    /**
     * Returns a record with scalar/vector data for UBO upload.
     * The renderer resolves textures to bindless handles and builds the final GPU struct.
     */
    Record scalarData();

    /** Returns textures referenced by this material, keyed by slot name. */
    default java.util.Map<String, dev.engine.core.asset.TextureData> textures() {
        return java.util.Map.of();
    }
}
