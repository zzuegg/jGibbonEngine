package dev.engine.tests.screenshot.scenes.textures;

import dev.engine.core.asset.TextureData;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.sampler.FilterMode;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.sampler.WrapMode;
import dev.engine.graphics.texture.SampledTexture;
import dev.engine.graphics.texture.TextureKeys;
import dev.engine.tests.screenshot.scenes.RenderTestScene;

import java.nio.ByteBuffer;

/**
 * Tests that per-texture sampler configurations produce visually different results.
 */
public class SamplerScenes {

    /** Two quads with same 4x4 checkerboard — one nearest, one linear.
     *  Nearest should show sharp pixel edges, linear should be smooth. */
    public static final RenderTestScene NEAREST_VS_LINEAR = engine -> {
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
        left.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(tex, SamplerDescriptor.nearest())));
        left.add(Transform.at(-1.2f, 0, 0));

        // Right: linear filtering (blurred)
        var right = scene.createEntity();
        right.add(PrimitiveMeshes.quad());
        right.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(tex, SamplerDescriptor.linear())));
        right.add(Transform.at(1.2f, 0, 0));
    };

    /** Same texture with repeat vs clamp wrap modes on a 3x-tiled quad.
     *  Repeat should show the pattern 3 times, clamp should show it once with stretched edges. */
    public static final RenderTestScene REPEAT_VS_CLAMP = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var tex = createGradient(4, 4);
        // Left: repeat wrapping — texture tiles 3x
        var left = scene.createEntity();
        left.add(PrimitiveMeshes.quad(3f));
        left.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(tex,
                        new SamplerDescriptor(FilterMode.NEAREST, FilterMode.NEAREST,
                                WrapMode.REPEAT, WrapMode.REPEAT))));
        left.add(Transform.at(-1.2f, 0, 0));

        // Right: clamp to edge — texture stretches at edges
        var right = scene.createEntity();
        right.add(PrimitiveMeshes.quad(3f));
        right.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(tex,
                        new SamplerDescriptor(FilterMode.NEAREST, FilterMode.NEAREST,
                                WrapMode.CLAMP_TO_EDGE, WrapMode.CLAMP_TO_EDGE))));
        right.add(Transform.at(1.2f, 0, 0));
    };

    /** Verifies sampler switching between entities — nearest on first, linear on second.
     *  Tests that the sampler state doesn't leak between draw calls. */
    public static final RenderTestScene SAMPLER_SWITCHING = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 4), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var tex = createCheckerboard(4, 4);

        // Three quads alternating nearest/linear/nearest
        var left = scene.createEntity();
        left.add(PrimitiveMeshes.quad());
        left.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(tex, SamplerDescriptor.nearest())));
        left.add(Transform.at(-2, 0, 0));

        var center = scene.createEntity();
        center.add(PrimitiveMeshes.quad());
        center.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(tex, SamplerDescriptor.linear())));
        center.add(Transform.IDENTITY);

        var right = scene.createEntity();
        right.add(PrimitiveMeshes.quad());
        right.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(tex, SamplerDescriptor.nearest())));
        right.add(Transform.at(2, 0, 0));
    };

    private static TextureData createCheckerboard(int w, int h) {
        var pixels = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean lit = (x + y) % 2 == 0;
                byte v = lit ? (byte) 255 : (byte) 0;
                pixels.put(v).put(v).put(v).put((byte) 255);
            }
        }
        pixels.flip();
        return TextureData.rgba(w, h, pixels);
    }

    private static TextureData createGradient(int w, int h) {
        var pixels = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
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
