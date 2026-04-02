package dev.engine.core.material;

import dev.engine.core.asset.TextureData;
import dev.engine.core.math.Vec3;

import java.util.HashMap;
import java.util.Map;

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

    public record ScalarData(Vec3 color) {}

    @Override
    public Record scalarData() { return new ScalarData(color); }

    @Override
    public Map<String, TextureData> textures() {
        var map = new HashMap<String, TextureData>();
        if (colorMap != null) map.put("colorMap", colorMap);
        return map;
    }
}
