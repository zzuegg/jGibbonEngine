package dev.engine.tutorials.debug_ui;

import dev.engine.core.tutorial.Tutorial;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.Entity;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.opengl.OpenGlBackend;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.platform.desktop.DesktopPlatform;
import dev.engine.windowing.glfw.GlfwWindowToolkit;
import dev.engine.ui.NkColor;
import dev.engine.ui.NkContext;

/*
# Debug UI

The engine includes a built-in immediate-mode debug UI system inspired by
[Nuklear](https://github.com/Immediate-Mode-UI/Nuklear). It renders on top
of your 3D scene and works across all backends (OpenGL, Vulkan, WebGPU).

The UI is **immediate-mode** — you describe the interface every frame in your
`update()` method. No retained widget objects, no callbacks. Just if-statements.
*/

@Tutorial(
    title = "Debug UI",
    category = "Debug UI",
    order = 1,
    description = "Add an interactive debug UI overlay with windows, buttons, sliders, and more."
)
public class T01_DebugUI extends BaseApplication {

    /*
    ## State

    In immediate-mode UI, you own all the state. The UI system just reads and
    writes it each frame. Declare your state as regular fields:
    */

    private float cubeScale = 1.0f;
    private float cubeX = 0, cubeY = 0, cubeZ = 0;
    private boolean wireframe = false;
    private int materialChoice = 0;
    private Entity cube;

    /*
    ## Scene Setup

    Create a simple scene with a cube. The debug UI renders on top of whatever
    your scene contains.
    */

    @Override
    protected void init() {
        cube = scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.3f, 0.5f, 0.8f)));
        cube.add(Transform.IDENTITY);

        var ground = scene().createEntity();
        ground.add(PrimitiveMeshes.plane(10, 10));
        ground.add(MaterialData.unlit(new Vec3(0.15f, 0.15f, 0.2f)));
        ground.add(Transform.at(0, -1, 0).withScale(10f));

        camera().lookAt(new Vec3(0, 2, 5), Vec3.ZERO, Vec3.UNIT_Y);
        camera().setPerspective((float) Math.toRadians(60), 16f / 9f, 0.1f, 100f);
    }

    /*
    ## Drawing the UI

    Access the UI context via `debugUi()`. Each frame, call `begin()`/`end()`
    to create windows, and add widgets between them.

    ### Windows

    `begin()` creates a window. Pass the title, position, size, and flags.
    It returns `true` if the window is visible (not collapsed or closed).
    Always call `end()` — even if `begin()` returned false.
    */

    @Override
    protected void update(float deltaTime) {
        NkContext ui = debugUi();

        if (ui.begin("Inspector", 10, 10, 250, 300,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_MOVABLE
                | NkContext.WINDOW_TITLE | NkContext.WINDOW_MINIMIZABLE)) {

            /*
            ### Layout

            Before adding widgets, set the row layout. `layoutRowDynamic` creates
            rows where items share the width equally. The first argument is the
            row height, the second is the number of columns.
            */

            ui.layoutRowDynamic(20, 1);
            ui.label("Transform");
            ui.separator();

            /*
            ### Property Editors

            `propertyFloat` creates a drag-to-edit value with +/- buttons.
            You can also put multiple properties on one row:
            */

            ui.layoutRowDynamic(22, 3);
            cubeX = ui.propertyFloat("X", -5, cubeX, 5, 0.1f, 0.02f);
            cubeY = ui.propertyFloat("Y", -5, cubeY, 5, 0.1f, 0.02f);
            cubeZ = ui.propertyFloat("Z", -5, cubeZ, 5, 0.1f, 0.02f);

            ui.layoutRowDynamic(22, 1);
            cubeScale = ui.propertyFloat("Scale", 0.1f, cubeScale, 5, 0.1f, 0.01f);

            /*
            ### Sliders and Checkboxes
            */

            ui.layoutRowDynamic(20, 1);
            ui.label("Options");
            ui.separator();

            ui.layoutRowDynamic(20, 1);
            wireframe = ui.checkbox("Wireframe", wireframe);

            /*
            ### Combo Boxes
            */

            ui.layoutRowDynamic(22, 1);
            materialChoice = ui.combo(
                new String[]{"Blue", "Red", "Green"}, materialChoice, 18);
        }
        ui.end();

        /*
        ## Applying UI State to the Scene

        The UI just modifies your state variables. Apply them to the scene
        in the same `update()` method:
        */

        Vec3 color = switch (materialChoice) {
            case 1 -> new Vec3(0.8f, 0.2f, 0.2f);
            case 2 -> new Vec3(0.2f, 0.8f, 0.2f);
            default -> new Vec3(0.3f, 0.5f, 0.8f);
        };
        cube.add(MaterialData.unlit(color));
        cube.add(Transform.at(cubeX, cubeY, cubeZ).withScale(cubeScale));
    }

    /*
    ## Running the Example

    The launcher is the same as any engine application:
    */

    public static void main(String[] args) {
        var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        var config = EngineConfig.builder()
                .window(WindowDescriptor.builder("Debug UI Tutorial").size(800, 600).build())
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(OpenGlBackend.factory(toolkit,
                        new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings()))
                .build();
        new T01_DebugUI().launch(config);
    }

    /*
    ## What's Next?

    The debug UI supports many more widgets:

    - **Labels** — `label()`, `labelColored()`, aligned text
    - **Buttons** — `button()` returns true when clicked
    - **Sliders** — `sliderFloat()`, `sliderInt()`
    - **Progress bars** — `progress()`
    - **Text input** — `editString()`
    - **Color picker** — `colorPicker()`, `colorPalette()`
    - **Trees** — `treePush()`/`treePop()`, `treeNode()` with selection
    - **Sections** — `sectionBegin()`/`sectionEnd()` accordion headers
    - **Groups** — `groupBegin()`/`groupEnd()` scrollable areas
    - **Charts** — `chart()` live value graphs
    - **Tooltips** — `tooltip()` on hover

    All styling is customizable via `ui.style()`. Windows can be dragged,
    minimized, closed, and brought to front by clicking.

    See `UiSamplerExample.java` for a comprehensive showcase of every widget.
    */
}
