package dev.engine.examples;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.opengl.OpenGlBackend;
import dev.engine.platform.desktop.DesktopPlatform;
import dev.engine.windowing.glfw.GlfwWindowToolkit;
import dev.engine.ui.NkColor;
import dev.engine.ui.NkContext;

/**
 * Showcases every widget in the Nuklear-inspired debug UI system.
 *
 * <p>Run with:
 * <pre>
 * ./gradlew :examples:run -PmainClass=dev.engine.examples.UiSamplerExample
 * </pre>
 */
public class UiSamplerExample extends BaseApplication {

    // Widget state
    private boolean checkboxValue = true;
    private boolean checkbox2 = false;
    private float sliderFloat = 0.5f;
    private int sliderInt = 50;
    private float progress = 0.65f;
    private String editText = "Edit me!";
    private int comboSelected = 0;
    private float propertyValue = 1.0f;
    private boolean selectable1 = false;
    private boolean selectable2 = true;
    private boolean selectable3 = false;
    private NkColor pickedColor = NkColor.rgba(100, 150, 200, 255);
    private boolean treeOpen = true;
    private boolean subTreeOpen = false;

    // Scene state
    private dev.engine.core.scene.Entity cube;
    private float cubeRotation = 0;
    private float cubeScale = 1.0f;

    @Override
    protected void init() {
        // Scene background — a spinning cube to show the UI renders over 3D
        cube = scene().createEntity();
        cube.add(PrimitiveMeshes.cube());
        cube.add(MaterialData.unlit(new Vec3(0.3f, 0.5f, 0.8f)));
        cube.add(Transform.at(2, 0, 0));

        var ground = scene().createEntity();
        ground.add(PrimitiveMeshes.plane(10, 10));
        ground.add(MaterialData.unlit(new Vec3(0.12f, 0.12f, 0.15f)));
        ground.add(Transform.at(0, -1, 0).withScale(10f));

        camera().lookAt(new Vec3(0, 3, 8), new Vec3(0, 0, 0), Vec3.UNIT_Y);
        camera().setPerspective((float) Math.toRadians(60), 16f / 9f, 0.1f, 100f);
    }

    @Override
    protected void update(float deltaTime) {
        cubeRotation += deltaTime * 0.5f;

        NkContext ui = debugUi();

        // ═══════════════════════════════════════════════════════════
        // Panel 1: Basic Widgets
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Basic Widgets", 10, 10, 280, 420,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_TITLE | NkContext.WINDOW_MINIMIZABLE)) {

            // Labels
            ui.layoutRowDynamic(20, 1);
            ui.label("Labels & Text");
            ui.separator();

            ui.layoutRowDynamic(18, 1);
            ui.label("Left aligned", NkContext.TEXT_LEFT);
            ui.label("Center aligned", NkContext.TEXT_CENTER);
            ui.label("Right aligned", NkContext.TEXT_RIGHT);
            ui.labelColored("Colored label!", NkColor.rgba(255, 100, 50, 255));

            // Buttons
            ui.layoutRowDynamic(20, 1);
            ui.label("Buttons");
            ui.separator();

            ui.layoutRowDynamic(25, 2);
            if (ui.button("Button A")) {
                System.out.println("Button A clicked!");
            }
            if (ui.button("Button B")) {
                System.out.println("Button B clicked!");
            }

            // Checkboxes
            ui.layoutRowDynamic(20, 1);
            ui.label("Checkboxes");
            ui.separator();

            ui.layoutRowDynamic(20, 1);
            checkboxValue = ui.checkbox("Enable feature", checkboxValue);
            checkbox2 = ui.checkbox("Show advanced", checkbox2);

            // Text input
            ui.layoutRowDynamic(20, 1);
            ui.label("Text Input");
            ui.separator();

            ui.layoutRowDynamic(25, 1);
            editText = ui.editString(editText, 64);
        }
        ui.end();

        // ═══════════════════════════════════════════════════════════
        // Panel 2: Sliders, Progress, Properties
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Numeric Widgets", 300, 10, 280, 320,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_TITLE | NkContext.WINDOW_MINIMIZABLE)) {

            // Sliders
            ui.layoutRowDynamic(20, 1);
            ui.label("Sliders");
            ui.separator();

            ui.layoutRowDynamic(20, 1);
            ui.label(String.format("Float: %.2f", sliderFloat));
            ui.layoutRowDynamic(20, 1);
            sliderFloat = ui.sliderFloat(0, sliderFloat, 1, 0.01f);

            ui.layoutRowDynamic(20, 1);
            ui.label(String.format("Int: %d", sliderInt));
            ui.layoutRowDynamic(20, 1);
            sliderInt = ui.sliderInt(0, sliderInt, 100, 1);

            // Progress bar
            ui.layoutRowDynamic(20, 1);
            ui.label("Progress Bar");
            ui.separator();

            ui.layoutRowDynamic(20, 1);
            progress = ui.progress(progress, 1.0f, true);

            // Property editor (drag-to-change)
            ui.layoutRowDynamic(20, 1);
            ui.label("Property Editor");
            ui.separator();

            ui.layoutRowDynamic(25, 1);
            cubeScale = ui.propertyFloat("Scale", 0.1f, cubeScale, 5.0f, 0.1f, 0.01f);
        }
        ui.end();

        // ═══════════════════════════════════════════════════════════
        // Panel 3: Combo, Selectable, Color Picker
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Selection & Color", 300, 340, 280, 300,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_TITLE | NkContext.WINDOW_MINIMIZABLE)) {

            // Combo box
            ui.layoutRowDynamic(20, 1);
            ui.label("Combo Box");
            ui.separator();

            ui.layoutRowDynamic(25, 1);
            comboSelected = ui.combo(new String[]{"Option A", "Option B", "Option C", "Option D"}, comboSelected, 20);

            // Selectable labels
            ui.layoutRowDynamic(20, 1);
            ui.label("Selectable Labels");
            ui.separator();

            ui.layoutRowDynamic(20, 1);
            selectable1 = ui.selectableLabel("Item 1", selectable1);
            selectable2 = ui.selectableLabel("Item 2", selectable2);
            selectable3 = ui.selectableLabel("Item 3", selectable3);

            // Color picker
            ui.layoutRowDynamic(20, 1);
            ui.label("Color Picker");
            ui.separator();

            ui.layoutRowDynamic(80, 1);
            pickedColor = ui.colorPicker(pickedColor);
        }
        ui.end();

        // ═══════════════════════════════════════════════════════════
        // Panel 4: Tree, Groups, Tooltips
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Tree & Groups", 590, 10, 260, 350,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_TITLE | NkContext.WINDOW_CLOSABLE)) {

            // Tree
            ui.layoutRowDynamic(20, 1);
            ui.label("Tree Nodes");
            ui.separator();

            ui.layoutRowDynamic(20, 1);
            treeOpen = ui.treePush("Scene Graph", treeOpen);
            if (treeOpen) {
                ui.layoutRowDynamic(18, 1);
                ui.label("  Root");
                subTreeOpen = ui.treePush("Children", subTreeOpen);
                if (subTreeOpen) {
                    ui.layoutRowDynamic(18, 1);
                    ui.label("    Cube");
                    ui.label("    Ground");
                    ui.treePop();
                }
                ui.treePop();
            }

            // Groups
            ui.layoutRowDynamic(20, 1);
            ui.label("Scrollable Group");
            ui.separator();

            if (ui.groupBegin("scroll_group", NkContext.WINDOW_BORDER)) {
                for (int i = 0; i < 20; i++) {
                    ui.layoutRowDynamic(18, 1);
                    ui.label("Item " + i);
                }
                ui.groupEnd();
            }
        }
        ui.end();

        // ═══════════════════════════════════════════════════════════
        // Panel 5: Layout showcase
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Layout Examples", 590, 370, 260, 270,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_TITLE)) {

            // Dynamic row — items share width equally
            ui.layoutRowDynamic(20, 1);
            ui.label("Dynamic Layout (1 col)");
            ui.separator();

            ui.layoutRowDynamic(25, 3);
            ui.button("A");
            ui.button("B");
            ui.button("C");

            // Static row — fixed item width
            ui.layoutRowDynamic(20, 1);
            ui.label("Static Layout (60px items)");
            ui.separator();

            ui.layoutRowStatic(25, 60, 3);
            ui.button("X");
            ui.button("Y");
            ui.button("Z");

            // Mixed content in dynamic row
            ui.layoutRowDynamic(20, 1);
            ui.label("Mixed Content");
            ui.separator();

            ui.layoutRowDynamic(20, 2);
            ui.label("Name:");
            ui.label("Value");

            ui.layoutRowDynamic(20, 2);
            ui.label("Score:");
            ui.label(String.valueOf(sliderInt));

            ui.layoutRowDynamic(20, 2);
            ui.label("Scale:");
            ui.label(String.format("%.1f", cubeScale));
        }
        ui.end();

        // Tooltip demo — hover any panel to see tooltip
        if (ui.isWidgetHovered()) {
            ui.tooltip("This is a tooltip!");
        }

        // Apply cube scale and rotation from UI controls
        cube.add(Transform.at(2, 0, 0).withScale(cubeScale).rotatedY(cubeRotation));
    }

    // ═══════════════════════════════════════════════════════════════
    // Main entry point
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        var config = EngineConfig.builder()
                .windowTitle("UI Sampler — Debug UI Showcase")
                .windowSize(880, 660)
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(OpenGlBackend.factory(toolkit,
                        new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings()))
                .build();
        new UiSamplerExample().launch(config);
    }
}
