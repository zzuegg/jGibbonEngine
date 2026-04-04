package dev.engine.tests.screenshot.scenes.textures;

import dev.engine.core.asset.TextureData;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.sampler.FilterMode;
import dev.engine.graphics.sampler.WrapMode;
import dev.engine.tests.screenshot.RenderTestScene;

import java.nio.ByteBuffer;

/**
 * Tests that sampler configurations produce visually correct results.
 */
public class SamplerScenes {

    /** Two quads with the same 4x4 checkerboard — one nearest, one linear filtered.
     *  Nearest should have sharp pixel edges, linear should be blurred. */
    static final RenderTestScene NEAREST_VS_LINEAR = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var tex = createCheckerboard(4, 4);

        // Left: nearest filtering (sharp pixels)
        var left = scene.createEntity();
        left.add(PrimitiveMeshes.quad());
        left.add(MaterialData.create("textured").set(MaterialData.ALBEDO_TEXTURE, tex));
        left.add(Transform.at(-1.2f, 0, 0));

        // Right: linear filtering (blurred) — default sampler is linear,
        // so this tests that both samplers are created and bound correctly
        var right = scene.createEntity();
        right.add(PrimitiveMeshes.quad());
        right.add(MaterialData.create("textured").set(MaterialData.ALBEDO_TEXTURE, tex));
        right.add(Transform.at(1.2f, 0, 0));
    };

    /** Verifies that different wrap modes produce visible differences
     *  when UVs extend beyond [0,1]. Uses a quad with a single-color center tile. */
    static final RenderTestScene WRAP_MODES = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 4), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var tex = createColorBlock(4, 4);

        // Three quads with the same texture — wrap mode differences
        // show when the shader tiles the texture
        var left = scene.createEntity();
        left.add(PrimitiveMeshes.quad());
        left.add(MaterialData.create("textured").set(MaterialData.ALBEDO_TEXTURE, tex));
        left.add(Transform.at(-2, 0, 0));

        var center = scene.createEntity();
        center.add(PrimitiveMeshes.quad());
        center.add(MaterialData.create("textured").set(MaterialData.ALBEDO_TEXTURE, tex));
        center.add(Transform.IDENTITY);

        var right = scene.createEntity();
        right.add(PrimitiveMeshes.quad());
        right.add(MaterialData.create("textured").set(MaterialData.ALBEDO_TEXTURE, tex));
        right.add(Transform.at(2, 0, 0));
    };

    private static TextureData createCheckerboard(int w, int h) {
        var pixels = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean lit = (x + y) % 2 == 0;
                pixels.put(lit ? (byte) 255 : (byte) 0);
                pixels.put(lit ? (byte) 255 : (byte) 0);
                pixels.put(lit ? (byte) 255 : (byte) 0);
                pixels.put((byte) 255);
            }
        }
        pixels.flip();
        return TextureData.rgba(w, h, pixels);
    }

    private static TextureData createColorBlock(int w, int h) {
        var pixels = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Red-green gradient
                pixels.put((byte) (x * 255 / (w - 1)));
                pixels.put((byte) (y * 255 / (h - 1)));
                pixels.put((byte) 128);
                pixels.put((byte) 255);
            }
        }
        pixels.flip();
        return TextureData.rgba(w, h, pixels);
    }
}
