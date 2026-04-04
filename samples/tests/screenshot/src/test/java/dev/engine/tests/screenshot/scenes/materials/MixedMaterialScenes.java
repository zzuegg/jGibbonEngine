package dev.engine.tests.screenshot.scenes.materials;

import dev.engine.core.asset.TextureData;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.tests.screenshot.RenderTestScene;

import java.nio.ByteBuffer;

/**
 * Tests multiple different materials and shaders in the same frame.
 */
public class MixedMaterialScenes {

    /** Unlit + PBR + textured in same frame — tests shader/material switching per entity. */
    static final RenderTestScene MIXED_SHADERS_SAME_FRAME = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 8), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Unlit red cube
        var unlitCube = scene.createEntity();
        unlitCube.add(PrimitiveMeshes.cube());
        unlitCube.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        unlitCube.add(Transform.at(-3, 0, 0));

        // PBR sphere
        var pbrSphere = scene.createEntity();
        pbrSphere.add(PrimitiveMeshes.sphere());
        pbrSphere.add(MaterialData.pbr(new Vec3(0.8f, 0.8f, 0.8f), 0.3f, 0.8f));
        pbrSphere.add(Transform.IDENTITY);

        // Textured quad
        var texData = createCheckerboard(8, 8, (byte) 0, (byte) 128, (byte) 255);
        var texturedQuad = scene.createEntity();
        texturedQuad.add(PrimitiveMeshes.quad());
        texturedQuad.add(MaterialData.create("textured").set(MaterialData.ALBEDO_TEXTURE, texData));
        texturedQuad.add(Transform.at(3, 0, 0));
    };

    /** Many entities with same material — tests batching / no per-entity state leak. */
    static final RenderTestScene MANY_SAME_MATERIAL = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 8, 12), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var mat = MaterialData.unlit(new Vec3(0.3f, 0.6f, 0.9f));
        for (int z = -2; z <= 2; z++) {
            for (int x = -2; x <= 2; x++) {
                var cube = scene.createEntity();
                cube.add(PrimitiveMeshes.cube());
                cube.add(mat);
                cube.add(Transform.at(x * 2f, 0, z * 2f));
            }
        }
    };

    /** Each entity has a different PBR roughness — gradient from smooth to rough. */
    static final RenderTestScene PBR_ROUGHNESS_GRADIENT = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 8), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        for (int i = 0; i < 5; i++) {
            float roughness = i / 4f;
            var sphere = scene.createEntity();
            sphere.add(PrimitiveMeshes.sphere());
            sphere.add(MaterialData.pbr(new Vec3(0.8f, 0.3f, 0.1f), roughness, 0.5f));
            sphere.add(Transform.at((i - 2) * 2f, 0, 0));
        }
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
