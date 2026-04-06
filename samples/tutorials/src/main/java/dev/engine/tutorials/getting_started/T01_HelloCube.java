package dev.engine.tutorials.getting_started;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.core.tutorial.Tutorial;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.opengl.OpenGlBackend;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.platform.desktop.DesktopPlatform;

/*
 * # Your First Cube
 *
 * This tutorial shows the absolute minimum needed to get a 3D cube
 * on screen. We'll cover:
 *
 * - Creating an application class
 * - Setting up a camera
 * - Adding a cube entity to the scene
 * - Running the engine
 */
@Tutorial(
        title = "Hello Cube",
        category = "Getting Started",
        order = 1,
        description = "The minimum code to render a 3D cube — your first jGibbonEngine application."
)
public class T01_HelloCube extends BaseApplication {

    /*
     * ## The init() method
     *
     * `init()` is called once after the engine starts. This is where you set up
     * your scene — create entities, configure the camera, load assets.
     *
     * Every jGibbonEngine app extends `BaseApplication` and overrides `init()`.
     */
    @Override
    protected void init() {

        /*
         * ### Set up the camera
         *
         * The camera determines what you see. `lookAt` positions the camera
         * and points it at a target. Without a camera, nothing is visible.
         */
        camera().lookAt(
                new Vec3(0, 2, 5),   // camera position: slightly above and in front
                Vec3.ZERO,            // look at the origin
                Vec3.UNIT_Y           // "up" direction is +Y
        );

        /*
         * ### Create an entity
         *
         * Everything in the scene is an **entity** with **components**.
         * An entity needs at least:
         *
         * - A **mesh** — the shape (cube, sphere, custom model)
         * - A **material** — the appearance (color, texture, PBR properties)
         * - A **transform** — the position, rotation, scale
         */
        var cube = scene().createEntity();

        /* Add a cube mesh. `PrimitiveMeshes` provides built-in shapes. */
        cube.add(PrimitiveMeshes.cube());

        /* Add an unlit material with a blue color. Unlit means no lighting
         * calculations — the color is shown as-is. Great for debugging. */
        cube.add(MaterialData.unlit(new Vec3(0.2f, 0.6f, 1.0f)));

        /* Place the cube at the origin. `Transform.IDENTITY` means
         * no translation, no rotation, scale = 1. */
        cube.add(Transform.IDENTITY);
    }

    /*
     * ## The update() method
     *
     * `update()` is called every frame. Use it for game logic, animation,
     * or anything that changes over time. Here we just update the camera
     * aspect ratio to match the window size.
     */
    @Override
    protected void update(float deltaTime) {
        float aspect = (float) window().width() / Math.max(window().height(), 1);
        camera().setPerspective(
                (float) Math.toRadians(60),  // 60° field of view
                aspect,                       // match window aspect ratio
                0.1f,                         // near clip plane
                100f                          // far clip plane
        );
    }

    /*
     * ## The main() method
     *
     * This is the entry point. We configure the engine and launch the app.
     *
     * `EngineConfig` bundles everything:
     * - **Platform** — how assets are loaded and shaders compiled
     * - **GraphicsBackend** — which GPU API to use (OpenGL, Vulkan, WebGPU)
     * - **Window settings** — title, size
     */
    public static void main(String[] args) {
        var config = EngineConfig.builder()
                .window(WindowDescriptor.builder("Tutorial 01 — Hello Cube").size(1280, 720).build())
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(OpenGlBackend.factory(
                        new dev.engine.windowing.glfw.GlfwWindowToolkit(dev.engine.windowing.glfw.GlfwWindowToolkit.OPENGL_HINTS),
                        new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings()))
                .build();

        /* Launch! This opens the window, calls init(), then runs the
         * update loop until the window is closed. */
        new T01_HelloCube().launch(config);
    }
}
