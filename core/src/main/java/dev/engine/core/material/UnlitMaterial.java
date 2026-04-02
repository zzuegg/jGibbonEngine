package dev.engine.core.material;

import dev.engine.core.asset.TextureData;
import dev.engine.core.math.Vec3;

/**
 * Unlit material — no lighting, just color or texture.
 */
public record UnlitMaterial(
        Vec3 color,
        TextureData colorMap
) implements MaterialData {

    public static UnlitMaterial color(Vec3 color) { return new UnlitMaterial(color, null); }
    public static UnlitMaterial white() { return color(Vec3.ONE); }
    public static UnlitMaterial textured(TextureData map) { return new UnlitMaterial(Vec3.ONE, map); }
}
