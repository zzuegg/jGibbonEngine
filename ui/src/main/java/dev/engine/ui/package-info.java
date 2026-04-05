@EngineModule(
    name = "Debug UI",
    description = "Immediate-mode debug UI overlay — windows, widgets, inspectors, and charts. Renders on all backends.",
    category = "UI",
    features = {"Windows", "Buttons & Checkboxes", "Sliders & Progress", "Tree View", "Color Picker", "Live Charts", "Inspector Panels"},
    icon = "🖥️"
)
@EngineFeature(
    name = "Debug UI Overlay",
    description = "Built-in Nuklear-inspired immediate-mode UI with draggable windows, z-ordering, and input focus. Works on OpenGL, Vulkan, and WebGPU.",
    icon = "🖥️"
)
@EngineFeature(
    name = "Inspector Panels",
    description = "Accordion-style collapsible sections with property grids — label-value pairs, drag-to-edit floats, color pickers, and combo boxes.",
    icon = "🔍"
)
@EngineFeature(
    name = "Live Charts",
    description = "Ring-buffer line graphs for real-time data — FPS counters, frame time, or any streaming values with auto-range.",
    icon = "📊"
)
package dev.engine.ui;

import dev.engine.core.docs.EngineModule;
import dev.engine.core.docs.EngineFeature;
