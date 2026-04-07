package dev.engine.tests.screenshot.web;

import dev.engine.tests.screenshot.scenes.RenderTestScene;
import dev.engine.tests.screenshot.scenes.SceneConfig;
import dev.engine.tests.screenshot.scenes.basic.BasicScenes;
import dev.engine.tests.screenshot.scenes.basic.CameraScenes;
import dev.engine.tests.screenshot.scenes.basic.HierarchyScenes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit scene registry for TeaVM. References scene fields directly
 * (TeaVM has no reflection for discovery). Scene fields must be public.
 */
public final class WebTestSceneRegistry {

    private WebTestSceneRegistry() {}

    public static Map<String, SceneEntry> all() {
        var map = new LinkedHashMap<String, SceneEntry>();

        // Basic scenes
        put(map, "depth_test_cubes", BasicScenes.DEPTH_TEST_CUBES);
        put(map, "two_cubes_unlit", BasicScenes.TWO_CUBES_UNLIT);
        put(map, "primitive_meshes", BasicScenes.PRIMITIVE_MESHES);

        // Camera scenes
        put(map, "close_perspective", CameraScenes.CLOSE_PERSPECTIVE);
        put(map, "top_down_view", CameraScenes.TOP_DOWN_VIEW);
        put(map, "wide_fov", CameraScenes.WIDE_FOV);

        // Hierarchy scenes
        put(map, "parent_child_transform", HierarchyScenes.PARENT_CHILD_TRANSFORM);
        put(map, "multi_child", HierarchyScenes.MULTI_CHILD);

        return map;
    }

    private static void put(Map<String, SceneEntry> map, String name, RenderTestScene scene) {
        map.put(name, new SceneEntry(scene, scene.config()));
    }

    public record SceneEntry(RenderTestScene scene, SceneConfig config) {}
}
