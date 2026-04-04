package dev.engine.tests.screenshot.scenes.textures;

import dev.engine.core.asset.TextureData;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import dev.engine.tests.screenshot.RenderTestScene;

import java.nio.ByteBuffer;

public class TextureScenes {

    static final RenderTestScene TEXTURED_QUAD = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var texData = createCheckerboard(8, 8, (byte) 255, (byte) 255, (byte) 255);
        var quad = scene.createEntity();
        quad.add(PrimitiveMeshes.quad());
        quad.add(MaterialData.create("textured")
                .set(MaterialData.ALBEDO_TEXTURE, texData));
        quad.add(Transform.IDENTITY);
    };

    static final RenderTestScene MATERIAL_TEXTURE = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
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
                .set(MaterialData.ALBEDO_TEXTURE, texData));
        quad.add(Transform.IDENTITY);
    };

    static final RenderTestScene TEXTURE_SWITCHING = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 4), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var leftQuad = scene.createEntity();
        leftQuad.add(PrimitiveMeshes.quad());
        leftQuad.add(MaterialData.create("textured")
                .set(MaterialData.ALBEDO_TEXTURE, createCheckerboard(8, 8, (byte) 255, (byte) 0, (byte) 0)));
        leftQuad.add(Transform.at(-1.5f, 0, 0));

        var rightQuad = scene.createEntity();
        rightQuad.add(PrimitiveMeshes.quad());
        rightQuad.add(MaterialData.create("textured")
                .set(MaterialData.ALBEDO_TEXTURE, createCheckerboard(8, 8, (byte) 0, (byte) 0, (byte) 255)));
        rightQuad.add(Transform.at(1.5f, 0, 0));
    };

    static final RenderTestScene TEXTURE_3D_CREATE = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Create and upload a 4x4x4 3D texture (proves the path works)
        var desc3d = TextureDescriptor.texture3d(4, 4, 4, TextureFormat.RGBA8);
        var tex3d = renderer.gpu().createTexture(desc3d);
        var pixels = ByteBuffer.allocateDirect(4 * 4 * 4 * 4);
        for (int i = 0; i < 4 * 4 * 4; i++) {
            pixels.put((byte) 255).put((byte) 128).put((byte) 0).put((byte) 255);
        }
        pixels.flip();
        renderer.gpu().uploadTexture(tex3d, pixels);
        renderer.gpu().destroyTexture(tex3d);

        var cube = scene.createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(1.0f, 0.5f, 0.0f)));
        cube.add(Transform.IDENTITY);
    };

    static final RenderTestScene TEXTURE_ARRAY_CREATE = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var descArr = TextureDescriptor.texture2dArray(4, 4, 3, TextureFormat.RGBA8);
        var texArr = renderer.gpu().createTexture(descArr);
        var pixels = ByteBuffer.allocateDirect(4 * 4 * 3 * 4);
        for (int i = 0; i < 4 * 4 * 3; i++) {
            pixels.put((byte) 0).put((byte) 255).put((byte) 128).put((byte) 255);
        }
        pixels.flip();
        renderer.gpu().uploadTexture(texArr, pixels);
        renderer.gpu().destroyTexture(texArr);

        var sphere = scene.createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.unlit(new Vec3(0.0f, 1.0f, 0.5f)));
        sphere.add(Transform.IDENTITY);
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
