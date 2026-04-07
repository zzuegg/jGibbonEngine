package dev.engine.tests.screenshot.scenes.textures;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import dev.engine.tests.screenshot.scenes.RenderTestScene;

/**
 * Tests for texture API features (3D textures, array textures).
 */
public class TextureApiScenes {

    /** Creates a 4x4x4 3D texture (verifies API), renders an orange cube alongside. */
    public static final RenderTestScene TEXTURE_3D_CREATE = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Create and upload a 4x4x4 3D texture (proves the path works)
        var desc3d = TextureDescriptor.texture3d(4, 4, 4, TextureFormat.RGBA8);
        var tex3d = renderer.gpu().createTexture(desc3d);
        var pixels = java.nio.ByteBuffer.allocateDirect(4 * 4 * 4 * 4);
        for (int i = 0; i < 4 * 4 * 4; i++) {
            pixels.put((byte) 255).put((byte) 128).put((byte) 0).put((byte) 255);
        }
        pixels.flip();
        renderer.gpu().uploadTexture(tex3d, pixels);
        renderer.gpu().destroyTexture(tex3d);

        // Render a visible cube to prove the scene works
        var cube = scene.createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(1.0f, 0.5f, 0.0f)));
        cube.add(Transform.IDENTITY);
    };

    /** Creates a 4x4 2D array texture with 3 layers (verifies API), renders a teal sphere. */
    public static final RenderTestScene TEXTURE_ARRAY_CREATE = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var descArr = TextureDescriptor.texture2dArray(4, 4, 3, TextureFormat.RGBA8);
        var texArr = renderer.gpu().createTexture(descArr);
        var pixels = java.nio.ByteBuffer.allocateDirect(4 * 4 * 3 * 4);
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
}
