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
}
