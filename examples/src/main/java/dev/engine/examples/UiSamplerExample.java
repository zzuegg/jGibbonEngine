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
    private int selectedTreeItem = -1;

    // Chart state — ring buffer for live FPS/frame time
    private final float[] fpsHistory = new float[120];
    private final float[] frameTimeHistory = new float[120];
    private int chartOffset = 0;
    private int chartCount = 0;

    // Accordion state
    private boolean accTransform = true;
    private boolean accMaterial = true;
    private boolean accLighting = false;
    private boolean accPhysics = false;
    private boolean accAudio = false;
    private boolean accMeta = false;
    private float lightIntensity = 0.8f;
    private float lightAngle = 45f;
    private int materialType = 0;
    private String entityName = "MyCube";
    private String entityTag = "player";

    // Scene state
    private dev.engine.core.scene.Entity cube;
    private float cubeRotation = 0;
    private float cubeScale = 1.0f;
    private float cubeX = 2, cubeY = 0, cubeZ = 0;

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

        // Update chart ring buffer
        float fps = deltaTime > 0 ? 1.0f / deltaTime : 0;
        int writeIdx = (chartOffset + chartCount) % fpsHistory.length;
        fpsHistory[writeIdx] = fps;
        frameTimeHistory[writeIdx] = deltaTime * 1000; // ms
        if (chartCount < fpsHistory.length) {
            chartCount++;
        } else {
            chartOffset = (chartOffset + 1) % fpsHistory.length;
        }

        // ═══════════════════════════════════════════════════════════
        // Panel 1: Basic Widgets
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Basic Widgets", 10, 10, 280, 420,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_MOVABLE | NkContext.WINDOW_TITLE | NkContext.WINDOW_MINIMIZABLE)) {

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
        if (ui.begin("Numeric Widgets", 300, 10, 280, 460,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_MOVABLE | NkContext.WINDOW_TITLE | NkContext.WINDOW_MINIMIZABLE)) {

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

            // Cube position
            ui.layoutRowDynamic(20, 1);
            ui.label("Cube Position");
            ui.separator();

            ui.layoutRowDynamic(25, 3);
            cubeX = ui.propertyFloat("X", -10, cubeX, 10, 0.1f, 0.02f);
            cubeY = ui.propertyFloat("Y", -10, cubeY, 10, 0.1f, 0.02f);
            cubeZ = ui.propertyFloat("Z", -10, cubeZ, 10, 0.1f, 0.02f);
        }
        ui.end();

        // ═══════════════════════════════════════════════════════════
        // Panel 3: Combo, Selectable, Color Picker
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Selection & Color", 300, 340, 280, 400,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_MOVABLE | NkContext.WINDOW_TITLE | NkContext.WINDOW_MINIMIZABLE)) {

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

            // HSV Color picker
            ui.layoutRowDynamic(20, 1);
            ui.label("Color Picker (HSV)");
            ui.separator();

            ui.layoutRowDynamic(80, 1);
            pickedColor = ui.colorPicker(pickedColor);

            // Quick palette
            ui.layoutRowDynamic(20, 1);
            ui.label("Color Palette");
            ui.separator();

            ui.layoutRowDynamic(32, 1);
            pickedColor = ui.colorPalette(pickedColor, new NkColor[]{
                    NkColor.rgb(255, 0, 0), NkColor.rgb(255, 128, 0), NkColor.rgb(255, 255, 0),
                    NkColor.rgb(0, 255, 0), NkColor.rgb(0, 255, 255), NkColor.rgb(0, 128, 255),
                    NkColor.rgb(0, 0, 255), NkColor.rgb(128, 0, 255), NkColor.rgb(255, 0, 255),
                    NkColor.rgb(255, 255, 255), NkColor.rgb(128, 128, 128), NkColor.rgb(0, 0, 0),
            });
        }
        ui.end();

        // ═══════════════════════════════════════════════════════════
        // Panel 4: Tree, Groups, Tooltips
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Tree & Groups", 590, 10, 260, 350,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_MOVABLE | NkContext.WINDOW_TITLE | NkContext.WINDOW_CLOSABLE)) {

            // Tree
            ui.layoutRowDynamic(20, 1);
            ui.label("Tree Nodes");
            ui.separator();

            ui.layoutRowDynamic(20, 1);
            treeOpen = ui.treePush("Scene Graph", treeOpen);
            if (treeOpen) {
                ui.layoutRowDynamic(18, 1);
                if (ui.selectableLabel("  Root", selectedTreeItem == 0)) selectedTreeItem = 0;
                subTreeOpen = ui.treePush("Children", subTreeOpen);
                if (subTreeOpen) {
                    ui.layoutRowDynamic(18, 1);
                    if (ui.selectableLabel("    Cube", selectedTreeItem == 1)) selectedTreeItem = 1;
                    if (ui.selectableLabel("    Ground", selectedTreeItem == 2)) selectedTreeItem = 2;
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
                NkContext.WINDOW_BORDER | NkContext.WINDOW_MOVABLE | NkContext.WINDOW_TITLE)) {

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

        // ═══════════════════════════════════════════════════════════
        // Panel 6: Live Charts
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Performance", 10, 440, 280, 210,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_MOVABLE | NkContext.WINDOW_TITLE | NkContext.WINDOW_MINIMIZABLE)) {

            ui.layoutRowDynamic(16, 1);
            ui.label(String.format("FPS: %.0f", fps));

            ui.layoutRowDynamic(60, 1);
            ui.chart(fpsHistory, chartCount, chartOffset, NkColor.rgba(100, 200, 100, 255));

            ui.layoutRowDynamic(16, 1);
            ui.label(String.format("Frame: %.1f ms", deltaTime * 1000));

            ui.layoutRowDynamic(60, 1);
            ui.chart(frameTimeHistory, chartCount, chartOffset, NkColor.rgba(230, 160, 60, 255));
        }
        ui.end();

        // ═══════════════════════════════════════════════════════════
        // Panel 7: Accordion Inspector (side panel style)
        // ═══════════════════════════════════════════════════════════
        if (ui.begin("Inspector", 860, 10, 240, 640,
                NkContext.WINDOW_BORDER | NkContext.WINDOW_MOVABLE | NkContext.WINDOW_TITLE
                        | NkContext.WINDOW_MINIMIZABLE)) {

            // ── Transform section ──
            ui.layoutRowDynamic(22, 1);
            if (ui.sectionBegin("Transform", accTransform)) {
                accTransform = true;
                ui.layoutRowDynamic(22, 3);
                cubeX = ui.propertyFloat("X", -10, cubeX, 10, 0.1f, 0.02f);
                cubeY = ui.propertyFloat("Y", -10, cubeY, 10, 0.1f, 0.02f);
                cubeZ = ui.propertyFloat("Z", -10, cubeZ, 10, 0.1f, 0.02f);

                ui.layoutRowDynamic(22, 1);
                cubeScale = ui.propertyFloat("Scale", 0.1f, cubeScale, 5.0f, 0.1f, 0.01f);

                ui.layoutRowDynamic(18, 2);
                ui.label("Rotation:");
                ui.label(String.format("%.0f°", Math.toDegrees(cubeRotation) % 360));
                ui.sectionEnd();
            } else { accTransform = false; }

            // ── Material section ──
            ui.layoutRowDynamic(22, 1);
            if (ui.sectionBegin("Material", accMaterial)) {
                accMaterial = true;
                ui.layoutRowDynamic(22, 1);
                materialType = ui.combo(new String[]{"Unlit", "PBR", "Textured"}, materialType, 18);

                ui.layoutRowDynamic(18, 1);
                ui.label("Color");
                ui.layoutRowDynamic(60, 1);
                pickedColor = ui.colorPicker(pickedColor);

                ui.layoutRowDynamic(24, 1);
                pickedColor = ui.colorPalette(pickedColor, new NkColor[]{
                        NkColor.rgb(255, 80, 80), NkColor.rgb(80, 200, 80), NkColor.rgb(80, 120, 255),
                        NkColor.rgb(255, 200, 50), NkColor.rgb(200, 80, 255), NkColor.rgb(255, 255, 255),
                });
                ui.sectionEnd();
            } else { accMaterial = false; }

            // ── Lighting section ──
            ui.layoutRowDynamic(22, 1);
            if (ui.sectionBegin("Lighting", accLighting)) {
                accLighting = true;
                ui.layoutRowDynamic(18, 1);
                ui.label(String.format("Intensity: %.1f", lightIntensity));
                ui.layoutRowDynamic(18, 1);
                lightIntensity = ui.sliderFloat(0, lightIntensity, 2, 0.05f);

                ui.layoutRowDynamic(18, 1);
                ui.label(String.format("Angle: %.0f°", lightAngle));
                ui.layoutRowDynamic(18, 1);
                lightAngle = ui.sliderFloat(0, lightAngle, 360, 1);
                ui.sectionEnd();
            } else { accLighting = false; }

            // ── Physics section (property grid demo) ──
            ui.layoutRowDynamic(22, 1);
            if (ui.sectionBegin("Physics", accPhysics)) {
                accPhysics = true;
                ui.propertyLabel("Gravity", "-9.81 m/s²");
                ui.propertyLabel("Mass", "1.0 kg");
                ui.propertyLabel("Friction", "0.5");
                ui.propertyLabel("Restitution", "0.3");
                checkboxValue = ui.propertyCheckbox("Kinematic", checkboxValue);
                ui.sectionEnd();
            } else { accPhysics = false; }

            // ── Metadata section (editable text properties) ──
            ui.layoutRowDynamic(22, 1);
            if (ui.sectionBegin("Metadata", accMeta)) {
                accMeta = true;
                entityName = ui.propertyEdit("Name", entityName, 32);
                entityTag = ui.propertyEdit("Tag", entityTag, 16);
                ui.propertyLabel("ID", "42");
                ui.propertyLabel("Layer", "Default");
                ui.sectionEnd();
            } else { accMeta = false; }

            // ── Audio section ──
            ui.layoutRowDynamic(22, 1);
            if (ui.sectionBegin("Audio", accAudio)) {
                accAudio = true;
                ui.layoutRowDynamic(18, 1);
                ui.label("No audio sources");
                if (ui.button("Add Source")) {
                    System.out.println("Add audio source clicked");
                }
                ui.sectionEnd();
            } else { accAudio = false; }
        }
        ui.end();

        // Tooltip demo — hover any panel to see tooltip
        if (ui.isWidgetHovered()) {
            ui.tooltip("This is a tooltip!");
        }

        // Apply cube scale and rotation from UI controls
        cube.add(Transform.at(cubeX, cubeY, cubeZ).withScale(cubeScale).rotatedY(cubeRotation));
    }

    // ═══════════════════════════════════════════════════════════════
    // Main entry point
    // ═══════════════════════════════════════════════════════════════

    public static void main(String[] args) {
        var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        var config = EngineConfig.builder()
                .windowTitle("UI Sampler — Debug UI Showcase")
                .windowSize(1120, 680)
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(OpenGlBackend.factory(toolkit,
                        new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings()))
                .build();
        new UiSamplerExample().launch(config);
    }
}
