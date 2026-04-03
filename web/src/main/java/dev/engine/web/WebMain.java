package dev.engine.web;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.SlangShaderLoader;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.webgpu.WgpuRenderDevice;
import dev.engine.providers.teavm.webgpu.FetchAssetSource;
import dev.engine.providers.teavm.webgpu.TeaVmShaderCompiler;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuBindings;
import dev.engine.providers.teavm.webgpu.TeaVmWgpuInit;
import dev.engine.providers.teavm.windowing.CanvasWindowToolkit;
import dev.engine.graphics.window.WindowDescriptor;

/**
 * Entry point for the TeaVM-compiled web application.
 * Uses the same BaseApplication as desktop — just different platform providers.
 */
public class WebMain extends BaseApplication {

    @Override
    protected void init() {
        // Set up fetch-based asset loading
        var assetManager = new AssetManager(Runnable::run);
        assetManager.addSource(new FetchAssetSource("assets/"));
        assetManager.registerLoader(new SlangShaderLoader());
        renderer().shaderManager().setAssetManager(assetManager);

        // Set up camera
        var cam = camera();
        cam.lookAt(new Vec3(0, 3, 6), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60),
                (float) window().width() / Math.max(window().height(), 1), 0.1f, 100f);

        // Set up scene — same as CrossBackendScenes.TWO_CUBES_UNLIT
        var cube1 = scene().createEntity();
        cube1.add(PrimitiveMeshes.cube());
        cube1.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        cube1.add(Transform.at(-1.5f, 0, 0));

        var cube2 = scene().createEntity();
        cube2.add(PrimitiveMeshes.cube());
        cube2.add(MaterialData.unlit(new Vec3(0.2f, 0.9f, 0.2f)));
        cube2.add(Transform.at(1.5f, 0, 0));
    }

    @org.teavm.jso.JSBody(script = "return document.getElementById('canvas').width;")
    private static native int getCanvasWidth();

    @org.teavm.jso.JSBody(script = "return document.getElementById('canvas').height;")
    private static native int getCanvasHeight();

    public static void main(String[] args) {
        // Load generated @NativeStruct metadata (triggers RecordRegistry registration)
        dev.engine.core.shader.params.EngineParams_NativeStruct.init();
        dev.engine.core.shader.params.CameraParams_NativeStruct.init();
        dev.engine.core.shader.params.ObjectParams_NativeStruct.init();

        // Initialize WebGPU
        var wgpuBindings = new TeaVmWgpuBindings();
        wgpuBindings.initialize();
        TeaVmWgpuInit.initAsync();

        var config = EngineConfig.builder()
                .windowTitle("Engine - WebGPU")
                .windowSize(getCanvasWidth(), getCanvasHeight())
                .build();

        var app = new WebMain();
        // Same as desktop: launch() creates toolkit + device + window via the factory
        app.launch(config, c -> {
            var toolkit = new CanvasWindowToolkit();
            var window = toolkit.createWindow(new WindowDescriptor(c.windowTitle(), c.windowWidth(), c.windowHeight()));
            var device = new WgpuRenderDevice(window, wgpuBindings);
            var compiler = new TeaVmShaderCompiler();
            return new BaseApplication.BackendInstance(toolkit, window, device, compiler);
        });
    }
}
