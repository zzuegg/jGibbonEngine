package dev.engine.tests.screenshot.web;

import dev.engine.tests.screenshot.RenderTestScene;
import dev.engine.tests.screenshot.Tolerance;

import java.util.ArrayList;
import java.util.List;

import dev.engine.tests.screenshot.scenes.basic.BasicScenes;
import dev.engine.tests.screenshot.scenes.basic.CameraScenes;
import dev.engine.tests.screenshot.scenes.basic.HierarchyScenes;
import dev.engine.tests.screenshot.scenes.input.InputTestScenes;
import dev.engine.tests.screenshot.scenes.materials.MaterialScenes;
import dev.engine.tests.screenshot.scenes.materials.MixedMaterialScenes;
import dev.engine.tests.screenshot.scenes.renderstate.PerEntityRenderStateScenes;
import dev.engine.tests.screenshot.scenes.renderstate.RenderStateScenes;
import dev.engine.tests.screenshot.scenes.textures.SamplerScenes;
import dev.engine.tests.screenshot.scenes.textures.TextureScenes;
import dev.engine.tests.screenshot.scenes.ui.UiScenes;

/**
 * Explicit scene registry for the TeaVM-compiled web test app.
 *
 * <p>TeaVM does not support classpath scanning ({@code ClassLoader.getResources()})
 * or reliable reflective field access ({@code getDeclaredFields()}).
 * Scenes must be registered explicitly here.
 *
 * <p><b>When adding new scene classes to the {@code scenes} module,
 * also register them here.</b>
 */
public class WebSceneRegistry {

    public record DiscoveredScene(String name, RenderTestScene scene, Tolerance tolerance) {}

    /**
     * Returns all registered test scenes.
     */
    public static List<DiscoveredScene> discoverScenes() {
        var scenes = new ArrayList<DiscoveredScene>();

        // basic
        scenes.add(scene("depth_test_cubes", BasicScenes.DEPTH_TEST_CUBES));
        scenes.add(scene("two_cubes_unlit", BasicScenes.TWO_CUBES_UNLIT));
        scenes.add(scene("primitive_meshes", BasicScenes.PRIMITIVE_MESHES));

        // camera
        scenes.add(scene("close_perspective", CameraScenes.CLOSE_PERSPECTIVE));
        scenes.add(scene("top_down_view", CameraScenes.TOP_DOWN_VIEW));
        scenes.add(scene("wide_fov", CameraScenes.WIDE_FOV));

        // hierarchy
        scenes.add(scene("parent_child_transform", HierarchyScenes.PARENT_CHILD_TRANSFORM));
        scenes.add(scene("multi_child", HierarchyScenes.MULTI_CHILD));

        // input
        scenes.add(scene("key_press_changes_color", InputTestScenes.KEY_PRESS_CHANGES_COLOR));

        // materials
        scenes.add(scene("pbr_materials", MaterialScenes.PBR_MATERIALS));
        scenes.add(scene("single_sphere_pbr", MaterialScenes.SINGLE_SPHERE_PBR));
        scenes.add(scene("shader_switching", MaterialScenes.SHADER_SWITCHING));
        scenes.add(scene("mixed_shaders_same_frame", MixedMaterialScenes.MIXED_SHADERS_SAME_FRAME));
        scenes.add(scene("many_same_material", MixedMaterialScenes.MANY_SAME_MATERIAL));
        scenes.add(scene("pbr_roughness_gradient", MixedMaterialScenes.PBR_ROUGHNESS_GRADIENT));

        // renderstate
        scenes.add(scene("mixed_render_states", RenderStateScenes.MIXED_RENDER_STATES));
        scenes.add(scene("depth_write_off", RenderStateScenes.DEPTH_WRITE_OFF));
        scenes.add(scene("blend_additive", RenderStateScenes.BLEND_ADDITIVE));
        scenes.add(scene("front_face_cw", RenderStateScenes.FRONT_FACE_CW));
        scenes.add(scene("all_blend_modes", RenderStateScenes.ALL_BLEND_MODES, RenderStateScenes.ALL_BLEND_MODES_TOLERANCE));
        scenes.add(scene("per_entity_depth", PerEntityRenderStateScenes.PER_ENTITY_DEPTH));
        scenes.add(scene("per_entity_cull", PerEntityRenderStateScenes.PER_ENTITY_CULL));
        scenes.add(scene("per_entity_blend", PerEntityRenderStateScenes.PER_ENTITY_BLEND));

        // textures
        scenes.add(scene("textured_quad", TextureScenes.TEXTURED_QUAD));
        scenes.add(scene("material_texture", TextureScenes.MATERIAL_TEXTURE));
        scenes.add(scene("texture_switching", TextureScenes.TEXTURE_SWITCHING));
        scenes.add(scene("nearest_vs_linear", SamplerScenes.NEAREST_VS_LINEAR));
        scenes.add(scene("repeat_vs_clamp", SamplerScenes.REPEAT_VS_CLAMP));
        scenes.add(scene("sampler_switching", SamplerScenes.SAMPLER_SWITCHING));

        // ui
        scenes.add(scene("debug_ui_window", UiScenes.DEBUG_UI_WINDOW, UiScenes.DEFAULT_TOLERANCE));

        return scenes;
    }

    private static DiscoveredScene scene(String name, RenderTestScene scene) {
        return new DiscoveredScene(name, scene, Tolerance.loose());
    }

    private static DiscoveredScene scene(String name, RenderTestScene scene, Tolerance tolerance) {
        return new DiscoveredScene(name, scene, tolerance);
    }
}
