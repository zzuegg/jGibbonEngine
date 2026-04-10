package dev.engine.tests.screenshot.graalwasm;

import dev.engine.tests.screenshot.scenes.RenderTestScene;
import dev.engine.tests.screenshot.scenes.SceneConfig;
import dev.engine.tests.screenshot.scenes.basic.BasicScenes;
import dev.engine.tests.screenshot.scenes.basic.CameraScenes;
import dev.engine.tests.screenshot.scenes.basic.HierarchyScenes;
import dev.engine.tests.screenshot.scenes.input.InputTestScenes;
import dev.engine.tests.screenshot.scenes.materials.MaterialScenes;
import dev.engine.tests.screenshot.scenes.materials.MixedMaterialScenes;
import dev.engine.tests.screenshot.scenes.module.ModuleSystemScenes;
import dev.engine.tests.screenshot.scenes.renderstate.DepthFuncScenes;
import dev.engine.tests.screenshot.scenes.renderstate.PerEntityRenderStateScenes;
import dev.engine.tests.screenshot.scenes.renderstate.RenderStateScenes;
import dev.engine.tests.screenshot.scenes.renderstate.StencilScenes;
import dev.engine.tests.screenshot.scenes.renderstate.WireframeScenes;
import dev.engine.tests.screenshot.scenes.textures.SamplerScenes;
import dev.engine.tests.screenshot.scenes.textures.TextureApiScenes;
import dev.engine.tests.screenshot.scenes.textures.TextureScenes;
import dev.engine.tests.screenshot.scenes.ui.UiScenes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Explicit scene registry for GraalVM Web Image. References scene fields directly
 * (native-image has no reflection for discovery). Must be kept in sync with
 * {@code WebTestSceneRegistry}.
 */
public final class GraalWasmTestSceneRegistry {

    private GraalWasmTestSceneRegistry() {}

    public static Map<String, SceneEntry> all() {
        var map = new LinkedHashMap<String, SceneEntry>();

        // Basic
        put(map, "depth_test_cubes", BasicScenes.DEPTH_TEST_CUBES);
        put(map, "two_cubes_unlit", BasicScenes.TWO_CUBES_UNLIT);
        put(map, "primitive_meshes", BasicScenes.PRIMITIVE_MESHES);

        // Camera
        put(map, "close_perspective", CameraScenes.CLOSE_PERSPECTIVE);
        put(map, "top_down_view", CameraScenes.TOP_DOWN_VIEW);
        put(map, "wide_fov", CameraScenes.WIDE_FOV);

        // Hierarchy
        put(map, "parent_child_transform", HierarchyScenes.PARENT_CHILD_TRANSFORM);
        put(map, "multi_child", HierarchyScenes.MULTI_CHILD);

        // Input
        put(map, "key_press_changes_color", InputTestScenes.KEY_PRESS_CHANGES_COLOR);

        // Materials
        put(map, "pbr_materials", MaterialScenes.PBR_MATERIALS);
        put(map, "single_sphere_pbr", MaterialScenes.SINGLE_SPHERE_PBR);
        put(map, "shader_switching", MaterialScenes.SHADER_SWITCHING);
        put(map, "mixed_shaders_same_frame", MixedMaterialScenes.MIXED_SHADERS_SAME_FRAME);
        put(map, "many_same_material", MixedMaterialScenes.MANY_SAME_MATERIAL);
        put(map, "pbr_roughness_gradient", MixedMaterialScenes.PBR_ROUGHNESS_GRADIENT);

        // Module system
        put(map, "parallel_modules_operational", ModuleSystemScenes.PARALLEL_MODULES_OPERATIONAL);

        // Render state
        put(map, "depth_func_always", DepthFuncScenes.DEPTH_FUNC_ALWAYS);
        put(map, "per_entity_depth", PerEntityRenderStateScenes.PER_ENTITY_DEPTH);
        put(map, "per_entity_cull", PerEntityRenderStateScenes.PER_ENTITY_CULL);
        put(map, "per_entity_blend", PerEntityRenderStateScenes.PER_ENTITY_BLEND);
        put(map, "mixed_render_states", RenderStateScenes.MIXED_RENDER_STATES);
        put(map, "depth_write_off", RenderStateScenes.DEPTH_WRITE_OFF);
        put(map, "blend_additive", RenderStateScenes.BLEND_ADDITIVE);
        put(map, "front_face_cw", RenderStateScenes.FRONT_FACE_CW);
        put(map, "all_blend_modes", RenderStateScenes.ALL_BLEND_MODES);
        put(map, "stencil_masking", StencilScenes.STENCIL_MASKING);
        put(map, "forced_wireframe", WireframeScenes.FORCED_WIREFRAME);

        // Textures
        put(map, "nearest_vs_linear", SamplerScenes.NEAREST_VS_LINEAR);
        put(map, "repeat_vs_clamp", SamplerScenes.REPEAT_VS_CLAMP);
        put(map, "sampler_switching", SamplerScenes.SAMPLER_SWITCHING);
        put(map, "texture_3d_create", TextureApiScenes.TEXTURE_3D_CREATE);
        put(map, "texture_array_create", TextureApiScenes.TEXTURE_ARRAY_CREATE);
        put(map, "textured_quad", TextureScenes.TEXTURED_QUAD);
        put(map, "material_texture", TextureScenes.MATERIAL_TEXTURE);
        put(map, "texture_switching", TextureScenes.TEXTURE_SWITCHING);

        // UI
        put(map, "debug_ui_window", UiScenes.DEBUG_UI_WINDOW);

        return map;
    }

    private static void put(Map<String, SceneEntry> map, String name, RenderTestScene scene) {
        map.put(name, new SceneEntry(scene, scene.config()));
    }

    public record SceneEntry(RenderTestScene scene, SceneConfig config) {}
}
