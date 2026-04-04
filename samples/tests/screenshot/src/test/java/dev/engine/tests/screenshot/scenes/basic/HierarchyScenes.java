package dev.engine.tests.screenshot.scenes.basic;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.HierarchicalScene;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.tests.screenshot.RenderTestScene;

/**
 * Tests parent-child entity transforms.
 */
public class HierarchyScenes {

    /** Parent offset + child offset should combine correctly. */
    static final RenderTestScene PARENT_CHILD_TRANSFORM = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 5, 10), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Parent at (2, 0, 0)
        var parent = scene.createEntity();
        parent.add(PrimitiveMeshes.cube());
        parent.add(MaterialData.unlit(new Vec3(0.8f, 0.2f, 0.2f)));
        parent.add(Transform.at(2, 0, 0));

        // Child at local (0, 2, 0) — world should be (2, 2, 0)
        var child = scene.createEntity();
        child.setParent(parent);
        child.add(PrimitiveMeshes.cube());
        child.add(MaterialData.unlit(new Vec3(0.2f, 0.8f, 0.2f)));
        child.add(Transform.at(0, 2, 0));

        // Grandchild at local (0, 0, 2) — world should be (2, 2, 2)
        var grandchild = scene.createEntity();
        grandchild.setParent(child);
        grandchild.add(PrimitiveMeshes.sphere());
        grandchild.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.8f)));
        grandchild.add(Transform.at(0, 0, 2));
    };

    /** Multiple children under one parent — all should be offset by parent transform. */
    static final RenderTestScene MULTI_CHILD = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();
        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 6, 10), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        var parent = scene.createEntity();
        parent.add(Transform.at(0, 1, 0));

        var colors = new Vec3[]{
                new Vec3(0.9f, 0.1f, 0.1f),
                new Vec3(0.1f, 0.9f, 0.1f),
                new Vec3(0.1f, 0.1f, 0.9f),
                new Vec3(0.9f, 0.9f, 0.1f),
        };
        for (int i = 0; i < 4; i++) {
            var child = scene.createEntity();
            child.setParent(parent);
            child.add(PrimitiveMeshes.cube());
            child.add(MaterialData.unlit(colors[i]));
            float x = (i - 1.5f) * 2f;
            child.add(Transform.at(x, 0, 0));
        }
    };
}
