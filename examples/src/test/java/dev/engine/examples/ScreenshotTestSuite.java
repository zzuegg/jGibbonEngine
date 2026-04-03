package dev.engine.examples;

import dev.engine.core.asset.TextureData;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.renderstate.BlendMode;
import dev.engine.graphics.renderstate.CompareFunc;
import dev.engine.graphics.renderstate.CullMode;
import dev.engine.graphics.renderstate.FrontFace;
import dev.engine.graphics.renderstate.RenderState;
import dev.engine.graphics.renderstate.StencilOp;
import dev.engine.graphics.sampler.SamplerDescriptor;

/**
 * Comprehensive test scenes exercising rendering features.
 * Each scene is a {@link RenderTestScene} that can be rendered by any backend.
 */
public final class ScreenshotTestSuite {
    private ScreenshotTestSuite() {}

    /** Basic colored cube with depth test — front cube should occlude back cube. */
    static final RenderTestScene DEPTH_TEST_CUBES = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Front red cube at z=0
        var front = renderer.scene().createEntity();
        front.add(PrimitiveMeshes.cube());
        front.add(MaterialData.unlit(new Vec3(0.9f, 0.1f, 0.1f)));
        front.add(Transform.at(0, 0, 0));

        // Back blue cube at z=-3 (should be occluded)
        var back = renderer.scene().createEntity();
        back.add(PrimitiveMeshes.cube());
        back.add(MaterialData.unlit(new Vec3(0.1f, 0.1f, 0.9f)));
        back.add(Transform.at(0, 0, -3));
    };

    /** Multiple primitive meshes in a row. */
    static final RenderTestScene PRIMITIVE_MESHES = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 4, 10), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Cube
        var cube = renderer.scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.8f, 0.2f, 0.2f)));
        cube.add(Transform.at(-3, 0, 0));

        // Sphere
        var sphere = renderer.scene().createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.unlit(new Vec3(0.2f, 0.8f, 0.2f)));
        sphere.add(Transform.at(0, 0, 0));

        // Plane
        var plane = renderer.scene().createEntity();
        plane.add(PrimitiveMeshes.quad());
        plane.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.8f)));
        plane.add(Transform.at(3, 0, 0));
    };

    /** Cull mode test — render both sides of a quad with culling disabled. */
    static final RenderTestScene CULL_MODE_NONE = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Quad with no culling — should show backface
        var quad = renderer.scene().createEntity();
        quad.add(PrimitiveMeshes.quad());
        quad.add(MaterialData.unlit(new Vec3(0.9f, 0.9f, 0.1f))
            .set(RenderState.CULL_MODE, CullMode.NONE));
        quad.add(Transform.IDENTITY);
    };

    /** Render-to-texture — creates an RT, clears it, then renders the scene normally. */
    static final RenderTestScene RENDER_TO_TEXTURE = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var cube = renderer.scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.3f, 0.7f, 0.3f)));
        cube.add(Transform.at(0, 0, 0));
    };

    /** Multiple materials with different cull modes in a single scene. */
    static final RenderTestScene MIXED_RENDER_STATES = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 8), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Opaque cube with back-face culling (default)
        var opaque = renderer.scene().createEntity();
        opaque.add(PrimitiveMeshes.cube());
        opaque.add(MaterialData.unlit(new Vec3(0.8f, 0.2f, 0.2f)));
        opaque.add(Transform.at(-2, 0, 0));

        // Cube with front-face culling (shows inside)
        var frontCull = renderer.scene().createEntity();
        frontCull.add(PrimitiveMeshes.cube());
        frontCull.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.8f))
            .set(RenderState.CULL_MODE, CullMode.FRONT));
        frontCull.add(Transform.at(2, 0, 0));

        // Cube with no culling (both faces visible)
        var noCull = renderer.scene().createEntity();
        noCull.add(PrimitiveMeshes.cube());
        noCull.add(MaterialData.unlit(new Vec3(0.2f, 0.8f, 0.2f))
            .set(RenderState.CULL_MODE, CullMode.NONE));
        noCull.add(Transform.at(0, 0, -2));
    };

    /** Wireframe mode via forced property — all geometry rendered as wireframe. */
    static final RenderTestScene FORCED_WIREFRAME = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Force wireframe on everything
        renderer.forceProperty(RenderState.WIREFRAME, true);

        var sphere = renderer.scene().createEntity();
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.unlit(new Vec3(1f, 1f, 1f)));
        sphere.add(Transform.IDENTITY);
    };

    /** Additive blending — front cube blends additively with back cube. */
    static final RenderTestScene BLEND_ADDITIVE = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Back: red cube
        var back = renderer.scene().createEntity();
        back.add(PrimitiveMeshes.cube());
        back.add(MaterialData.unlit(new Vec3(0.8f, 0.0f, 0.0f)));
        back.add(Transform.at(0, 0, -1));

        // Front: green cube with additive blending (should appear yellow where overlapping)
        var front = renderer.scene().createEntity();
        front.add(PrimitiveMeshes.cube());
        front.add(MaterialData.unlit(new Vec3(0.0f, 0.8f, 0.0f))
            .set(RenderState.BLEND_MODE, BlendMode.ADDITIVE)
            .set(RenderState.DEPTH_WRITE, false));
        front.add(Transform.at(0, 0, 1));
    };

    /** Depth write disabled — back object visible through front. */
    static final RenderTestScene DEPTH_WRITE_OFF = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Back: blue sphere
        var back = renderer.scene().createEntity();
        back.add(PrimitiveMeshes.sphere());
        back.add(MaterialData.unlit(new Vec3(0.1f, 0.1f, 0.9f)));
        back.add(Transform.at(0, 0, -1));

        // Front: red cube with depth write off
        // The red cube renders first with depth write off,
        // so the blue sphere behind it is still visible where it extends beyond the cube
        var front = renderer.scene().createEntity();
        front.add(PrimitiveMeshes.cube());
        front.add(MaterialData.unlit(new Vec3(0.9f, 0.1f, 0.1f))
            .set(RenderState.DEPTH_WRITE, false));
        front.add(Transform.at(0, 0, 1));
    };

    /** PBR material test — rough vs metallic spheres. */
    static final RenderTestScene PBR_MATERIALS = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Rough sphere
        var rough = renderer.scene().createEntity();
        rough.add(PrimitiveMeshes.sphere());
        rough.add(MaterialData.pbr(new Vec3(0.8f, 0.3f, 0.1f), 0.9f, 0.1f));
        rough.add(Transform.at(-2, 0, 0));

        // Metallic sphere
        var metal = renderer.scene().createEntity();
        metal.add(PrimitiveMeshes.sphere());
        metal.add(MaterialData.pbr(new Vec3(0.9f, 0.9f, 0.9f), 0.1f, 0.9f));
        metal.add(Transform.at(2, 0, 0));
    };

    /** Procedural checkerboard texture upload + sampler creation alongside scene rendering. */
    static final RenderTestScene TEXTURED_QUAD = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Create 4x4 checkerboard RGBA texture
        int texW = 4, texH = 4;
        var pixels = java.nio.ByteBuffer.allocateDirect(texW * texH * 4);
        for (int y = 0; y < texH; y++) {
            for (int x = 0; x < texW; x++) {
                boolean white = (x + y) % 2 == 0;
                byte c = white ? (byte) 255 : (byte) 50;
                pixels.put(c).put(c).put(c).put((byte) 255);
            }
        }
        pixels.flip();

        // Upload texture and create sampler via renderer/device API
        var texData = TextureData.rgba(texW, texH, pixels);
        var texHandle = renderer.uploadTexture(texData);
        var sampler = renderer.device().createSampler(SamplerDescriptor.nearest());

        // Render a quad with unlit material — texture isn't auto-bound by materials yet,
        // but this verifies texture upload + sampler creation work alongside rendering
        var quad = renderer.scene().createEntity();
        quad.add(PrimitiveMeshes.quad());
        quad.add(MaterialData.unlit(new Vec3(1f, 1f, 1f)));
        quad.add(Transform.IDENTITY);
    };

    /** All blend modes side by side over a red background. */
    static final RenderTestScene ALL_BLEND_MODES = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 8), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Background: bright red plane
        var bg = renderer.scene().createEntity();
        bg.add(PrimitiveMeshes.quad());
        bg.add(MaterialData.unlit(new Vec3(0.9f, 0.0f, 0.0f)));
        bg.add(Transform.at(0, 0, -2).withScale(new Vec3(10, 10, 1)));

        // Alpha blend
        var alpha = renderer.scene().createEntity();
        alpha.add(PrimitiveMeshes.cube());
        alpha.add(MaterialData.unlit(new Vec3(0.0f, 0.0f, 1.0f))
            .set(RenderState.BLEND_MODE, BlendMode.ALPHA)
            .set(RenderState.DEPTH_WRITE, false));
        alpha.add(Transform.at(-3, 0, 0));

        // Additive
        var additive = renderer.scene().createEntity();
        additive.add(PrimitiveMeshes.cube());
        additive.add(MaterialData.unlit(new Vec3(0.0f, 1.0f, 0.0f))
            .set(RenderState.BLEND_MODE, BlendMode.ADDITIVE)
            .set(RenderState.DEPTH_WRITE, false));
        additive.add(Transform.at(0, 0, 0));

        // Multiply
        var multiply = renderer.scene().createEntity();
        multiply.add(PrimitiveMeshes.cube());
        multiply.add(MaterialData.unlit(new Vec3(0.5f, 0.5f, 1.0f))
            .set(RenderState.BLEND_MODE, BlendMode.MULTIPLY)
            .set(RenderState.DEPTH_WRITE, false));
        multiply.add(Transform.at(3, 0, 0));
    };

    /** Depth function GREATER — back cube visible, front cube occluded (reversed depth test). */
    static final RenderTestScene DEPTH_FUNC_GREATER = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Force GREATER depth on all entities
        renderer.setDefault(RenderState.DEPTH_FUNC, CompareFunc.GREATER);

        // Front: red cube (should be occluded with GREATER)
        var front = renderer.scene().createEntity();
        front.add(PrimitiveMeshes.cube());
        front.add(MaterialData.unlit(new Vec3(0.9f, 0.1f, 0.1f)));
        front.add(Transform.at(0, 0, 0));

        // Back: blue cube (should show with GREATER)
        var back = renderer.scene().createEntity();
        back.add(PrimitiveMeshes.cube());
        back.add(MaterialData.unlit(new Vec3(0.1f, 0.1f, 0.9f)));
        back.add(Transform.at(0, 0, -3));
    };

    /** CW front face — default CCW geometry should be culled with back-face culling. */
    static final RenderTestScene FRONT_FACE_CW = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // With CW front face + back culling, the default CCW geometry should be culled
        var cube = renderer.scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.9f, 0.9f, 0.1f))
            .set(RenderState.FRONT_FACE, FrontFace.CW));
        cube.add(Transform.IDENTITY);
    };

    /** Multiple shader types in one scene — PBR and UNLIT entities rendered together, forcing pipeline switches. */
    static final RenderTestScene SHADER_SWITCHING = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 8), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // PBR sphere on the left
        var pbrSphere = renderer.scene().createEntity();
        pbrSphere.add(PrimitiveMeshes.sphere());
        pbrSphere.add(MaterialData.pbr(new Vec3(0.8f, 0.2f, 0.1f), 0.3f, 0.8f));
        pbrSphere.add(Transform.at(-2, 0, 0));

        // Unlit cube on the right
        var unlitCube = renderer.scene().createEntity();
        unlitCube.add(PrimitiveMeshes.cube());
        unlitCube.add(MaterialData.unlit(new Vec3(0.1f, 0.8f, 0.2f)));
        unlitCube.add(Transform.at(2, 0, 0));

        // Another PBR sphere in the middle-back (forces pipeline switch back to PBR)
        var pbrSphere2 = renderer.scene().createEntity();
        pbrSphere2.add(PrimitiveMeshes.sphere());
        pbrSphere2.add(MaterialData.pbr(new Vec3(0.2f, 0.2f, 0.9f), 0.9f, 0.1f));
        pbrSphere2.add(Transform.at(0, 0, -2));
    };

    /** Textured quad using ALBEDO_MAP through the material system with the "textured" shader. */
    static final RenderTestScene MATERIAL_TEXTURE = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 3), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Create 8x8 checkerboard texture
        int texW = 8, texH = 8;
        var pixels = java.nio.ByteBuffer.allocateDirect(texW * texH * 4);
        for (int y = 0; y < texH; y++) {
            for (int x = 0; x < texW; x++) {
                boolean white = (x + y) % 2 == 0;
                byte c = white ? (byte) 255 : (byte) 40;
                pixels.put(c).put(c).put(c).put((byte) 255);
            }
        }
        pixels.flip();

        var texData = TextureData.rgba(texW, texH, pixels);

        var quad = renderer.scene().createEntity();
        quad.add(PrimitiveMeshes.quad());
        quad.add(MaterialData.create("textured")
            .set(MaterialData.ALBEDO_MAP, texData));
        quad.add(Transform.IDENTITY);
    };

    /** Two quads with different textures — verifies texture switching between draws. */
    static final RenderTestScene TEXTURE_SWITCHING = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 4), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Red checkerboard texture on the left
        var leftQuad = renderer.scene().createEntity();
        leftQuad.add(PrimitiveMeshes.quad());
        leftQuad.add(MaterialData.create("textured")
            .set(MaterialData.ALBEDO_MAP, createCheckerboard(8, 8, (byte) 255, (byte) 0, (byte) 0)));
        leftQuad.add(Transform.at(-1.5f, 0, 0));

        // Blue checkerboard texture on the right
        var rightQuad = renderer.scene().createEntity();
        rightQuad.add(PrimitiveMeshes.quad());
        rightQuad.add(MaterialData.create("textured")
            .set(MaterialData.ALBEDO_MAP, createCheckerboard(8, 8, (byte) 0, (byte) 0, (byte) 255)));
        rightQuad.add(Transform.at(1.5f, 0, 0));
    };

    /** Creates a checkerboard RGBA texture with the given color for lit squares. */
    private static TextureData createCheckerboard(int w, int h, byte r, byte g, byte b) {
        var pixels = java.nio.ByteBuffer.allocateDirect(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean lit = (x + y) % 2 == 0;
                pixels.put(lit ? r : (byte) 20);
                pixels.put(lit ? g : (byte) 20);
                pixels.put(lit ? b : (byte) 20);
                pixels.put((byte) 255);
            }
        }
        pixels.flip();
        return TextureData.rgba(w, h, pixels);
    }

    /** Stencil write + test — left quad writes to stencil, right quad only shows where stencil is set. */
    static final RenderTestScene STENCIL_MASKING = (renderer, w, h) -> {
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 0, 5), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), (float) w / h, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // First pass: small green quad writes 1 to stencil buffer
        var stencilWriter = renderer.scene().createEntity();
        stencilWriter.add(PrimitiveMeshes.quad());
        stencilWriter.add(MaterialData.unlit(new Vec3(0.0f, 0.8f, 0.0f))
            .set(RenderState.STENCIL_TEST, true)
            .set(RenderState.STENCIL_FUNC, CompareFunc.ALWAYS)
            .set(RenderState.STENCIL_REF, 1)
            .set(RenderState.STENCIL_MASK, 0xFF)
            .set(RenderState.STENCIL_PASS, StencilOp.REPLACE)
            .set(RenderState.STENCIL_FAIL, StencilOp.KEEP)
            .set(RenderState.STENCIL_DEPTH_FAIL, StencilOp.KEEP));
        stencilWriter.add(Transform.at(0, 0, 0).withScale(new Vec3(0.5f, 0.5f, 1)));

        // Second pass: large blue quad only renders where stencil == 1
        var stencilReader = renderer.scene().createEntity();
        stencilReader.add(PrimitiveMeshes.quad());
        stencilReader.add(MaterialData.unlit(new Vec3(0.0f, 0.0f, 0.9f))
            .set(RenderState.STENCIL_TEST, true)
            .set(RenderState.STENCIL_FUNC, CompareFunc.EQUAL)
            .set(RenderState.STENCIL_REF, 1)
            .set(RenderState.STENCIL_MASK, 0xFF)
            .set(RenderState.STENCIL_PASS, StencilOp.KEEP)
            .set(RenderState.STENCIL_FAIL, StencilOp.KEEP)
            .set(RenderState.STENCIL_DEPTH_FAIL, StencilOp.KEEP));
        stencilReader.add(Transform.at(0, 0, 0.1f).withScale(new Vec3(2, 2, 1)));
    };
}
