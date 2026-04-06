package dev.engine.tests.screenshot.scenes.textures;

import dev.engine.core.asset.TextureData;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.sampler.SamplerDescriptor;
import dev.engine.graphics.texture.SampledTexture;
import dev.engine.graphics.texture.TextureKeys;
import dev.engine.tests.screenshot.RenderTestScene;

import java.nio.ByteBuffer;

public class TextureScenes {

    /** Textured quad with nearest filtering — sharp checkerboard pixels. */
    public static final RenderTestScene TEXTURED_QUAD = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var texData = createCheckerboard(8, 8, (byte) 255, (byte) 255, (byte) 255);
        var quad = scene.createEntity();
        quad.add(PrimitiveMeshes.quad());
        quad.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(texData, SamplerDescriptor.nearest())));
        quad.add(Transform.IDENTITY);
    };

    /** Colored checkerboard with linear filtering — smooth blending. */
    public static final RenderTestScene MATERIAL_TEXTURE = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        int texW = 4, texH = 4;
        var pixels = ByteBuffer.allocateDirect(texW * texH * 4);
        for (int y = 0; y < texH; y++) {
            for (int x = 0; x < texW; x++) {
                boolean checker = (x + y) % 2 == 0;
                pixels.put(checker ? (byte) 200 : (byte) 50);
                pixels.put(checker ? (byte) 100 : (byte) 150);
                pixels.put(checker ? (byte) 50 : (byte) 200);
                pixels.put((byte) 255);
            }
        }
        pixels.flip();
        var texData = TextureData.rgba(texW, texH, pixels);

        var quad = scene.createEntity();
        quad.add(PrimitiveMeshes.quad());
        quad.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(texData, SamplerDescriptor.linear())));
        quad.add(Transform.IDENTITY);
    };

    /** Two quads with different textures and different samplers —
     *  tests texture + sampler switching between draw calls. */
    public static final RenderTestScene TEXTURE_SWITCHING = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 4), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Left: red checkerboard with nearest filtering
        var leftQuad = scene.createEntity();
        leftQuad.add(PrimitiveMeshes.quad());
        leftQuad.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(
                        createCheckerboard(8, 8, (byte) 255, (byte) 0, (byte) 0),
                        SamplerDescriptor.nearest())));
        leftQuad.add(Transform.at(-1.5f, 0, 0));

        // Right: blue checkerboard with linear filtering
        var rightQuad = scene.createEntity();
        rightQuad.add(PrimitiveMeshes.quad());
        rightQuad.add(MaterialData.create("textured")
                .set(TextureKeys.ALBEDO_TEXTURE, new SampledTexture(
                        createCheckerboard(8, 8, (byte) 0, (byte) 0, (byte) 255),
                        SamplerDescriptor.linear())));
        rightQuad.add(Transform.at(1.5f, 0, 0));
    };

    private static TextureData createCheckerboard(int w, int h, byte r, byte g, byte b) {
        var pixels = ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean lit = (x + y) % 2 == 0;
                pixels.put(lit ? r : 0).put(lit ? g : 0).put(lit ? b : 0).put((byte) 255);
            }
        }
        pixels.flip();
        return TextureData.rgba(w, h, pixels);
    }
}
