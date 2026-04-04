package dev.engine.graphics.common.engine;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.input.*;
import dev.engine.core.module.ModuleManager;
import dev.engine.core.module.Time;
import dev.engine.core.profiler.Profiler;
import dev.engine.core.profiler.RenderStats;
import dev.engine.core.scene.AbstractScene;
import dev.engine.core.scene.camera.Camera;
import dev.engine.graphics.GraphicsBackend;
import dev.engine.graphics.common.Renderer;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Base class for engine applications. Extend this and override lifecycle methods.
 *
 * <p>Handles the main loop, window management, and provides access to all engine systems.
 *
 * <pre>
 * public class MyGame extends BaseApplication {
 *     {@literal @}Override protected void init() {
 *         var mesh = assets().loadSync("models/cube.obj", MeshData.class);
 *         var entity = scene().createEntity();
 *         renderer().setMesh(entity, ...);
 *     }
 *
 *     {@literal @}Override protected void update(float deltaTime, List&lt;InputEvent&gt; inputEvents) {
 *         for (var event : inputEvents) {
 *             switch (event) {
 *                 case InputEvent.KeyPressed e when e.keyCode() == KeyCode.ESCAPE -&gt; window().close();
 *                 default -&gt; {}
 *             }
 *         }
 *     }
 * }
 *
 * // Launch:
 * var config = EngineConfig.builder()
 *     .platform(DesktopPlatform.builder().build())
 *     .graphicsBackend(OpenGlBackend.factory(glBindings))
 *     .build();
 * new MyGame().launch(config);
 * </pre>
 */
public abstract class BaseApplication {

    private static final Logger log = LoggerFactory.getLogger(BaseApplication.class);

    private Engine engine;
    private WindowToolkit toolkit;
    private WindowHandle window;
    private InputSystem inputSystem;
    private Camera defaultCamera;

    /**
     * Launches the application with the given configuration.
     * The config provides platform, graphics backend, window settings, and scene type.
     */
    public void launch(EngineConfig config) {
        try {
            var windowDesc = new WindowDescriptor(
                    config.windowTitle(), config.windowSize().x(), config.windowSize().y());
            var gfxConfig = new dev.engine.graphics.GraphicsConfig(config.headless());
            var backend = config.graphicsBackend().create(windowDesc, gfxConfig);
            this.toolkit = backend.toolkit();
            this.window = backend.window();
            runInternal(config, backend);
        } catch (Exception e) {
            log.error("Failed to launch application", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Override this to create and register an InputProvider for the platform.
     * Called during initialization. Return null if no input provider is available.
     */
    protected InputProvider createInputProvider() {
        return null;
    }

    private void runInternal(EngineConfig config, GraphicsBackend backend) {
        this.engine = new Engine(config, config.platform(), backend.device());
        this.inputSystem = new DefaultInputSystem();

        var inputProvider = createInputProvider();
        if (inputProvider != null) {
            inputSystem.registerProvider(inputProvider);
        }

        // Default camera
        defaultCamera = engine.renderer().createCamera();

        window.show();

        try {
            init();

            log.info("Application started: {}", config.windowTitle());
            long lastTime = System.nanoTime();
            long frameCount = 0;

            while (window.isOpen() && (config.maxFrames() <= 0 || frameCount < config.maxFrames())) {
                frameCount++;
                long now = System.nanoTime();
                double delta = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                toolkit.pollEvents();

                var inputEvents = inputSystem.queue().drainInput();
                var windowEvents = inputSystem.queue().drainWindow();

                // Process window events
                for (var we : windowEvents) {
                    switch (we) {
                        case WindowEvent.Resized r -> engine.renderer().setViewport(r.width(), r.height());
                        default -> {}
                    }
                }

                engine.renderer().setViewport(window.width(), window.height());
                float aspect = (float) window.width() / Math.max(window.height(), 1);

                engine.setInputEvents(inputEvents);
                update((float) delta, inputEvents);
                engine.tick(delta);
            }
        } finally {
            cleanup();
            if (inputProvider != null) inputProvider.close();
            engine.shutdown();
            toolkit.close();
            log.info("Application stopped");
        }
    }

    // --- Lifecycle methods (override these) ---

    /** Called once after engine initialization. Load assets, create entities here. */
    protected void init() {}

    /** Called every frame with input events. Update game logic here. */
    protected void update(float deltaTime, List<InputEvent> inputEvents) {
        // Default: delegate to the no-arg version for backward compatibility
        update(deltaTime);
    }

    /** Called every frame. Override this if you don't need input events. */
    protected void update(float deltaTime) {}

    /** Called on shutdown. Release custom resources here. */
    protected void cleanup() {}

    // --- Accessors ---

    protected Engine engine() { return engine; }
    protected Renderer renderer() { return engine.renderer(); }
    protected AbstractScene scene() { return engine.scene(); }
    protected AssetManager assets() { return engine.assets(); }
    protected ModuleManager<Time> modules() { return engine.modules(); }
    protected Profiler profiler() { return engine.profiler(); }
    protected RenderStats renderStats() { return engine.renderStats(); }
    protected InputSystem inputSystem() { return inputSystem; }
    protected Camera camera() { return defaultCamera; }
    protected WindowHandle window() { return window; }
    protected double time() { return engine.totalTime(); }
    protected long frameNumber() { return engine.frameNumber(); }

}
