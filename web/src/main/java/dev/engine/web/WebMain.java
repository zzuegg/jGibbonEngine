package dev.engine.web;

import dev.engine.providers.teavm.webgpu.TeaVmWgpuBindings;
import dev.engine.providers.teavm.windowing.CanvasWindowToolkit;

/**
 * Entry point for the TeaVM-compiled web application.
 *
 * <p>TeaVM compiles this class to JavaScript. The generated JS is loaded
 * by {@code index.html} and bootstraps the engine with browser-native
 * WebGPU and an HTML canvas window.
 */
public class WebMain {

    public static void main(String[] args) {
        System.out.println("[Engine/Web] Starting...");

        var bindings = new TeaVmWgpuBindings();
        if (!bindings.isAvailable()) {
            System.err.println("[Engine/Web] WebGPU is not available in this browser.");
            return;
        }
        System.out.println("[Engine/Web] WebGPU available.");

        var windowing = new CanvasWindowToolkit();
        System.out.println("[Engine/Web] Canvas windowing ready.");

        // TODO: request adapter/device (async via @Async)
        // TODO: create Renderer with WgpuRenderDevice + CanvasWindowToolkit
        // TODO: enter requestAnimationFrame render loop

        System.out.println("[Engine/Web] Scaffold complete — rendering not yet wired.");
    }
}
