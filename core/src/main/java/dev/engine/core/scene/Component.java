package dev.engine.core.scene;

/**
 * Marker interface for all components.
 *
 * <p>Components are data attached to entities. Each entity has at most one
 * component per {@link #slotType()}. By default, the slot is the concrete class.
 * Override to share a slot across types (e.g. all material types share one slot).
 */
public interface Component {

    /**
     * The slot type this component occupies on an entity.
     * Only one component per slot type is allowed.
     *
     * <p>Default: the concrete class. Override for families:
     * <pre>
     * sealed interface MaterialData extends Component {
     *     default Class&lt;?&gt; slotType() { return MaterialData.class; }
     * }
     * </pre>
     */
    default Class<? extends Component> slotType() { return getClass(); }
}
