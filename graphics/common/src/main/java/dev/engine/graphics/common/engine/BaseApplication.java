package dev.engine.graphics.common.engine;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.input.InputState;
import dev.engine.core.module.ModuleManager;
import dev.engine.core.module.Time;
import dev.engine.core.profiler.Profiler;
import dev.engine.core.profiler.RenderStats;
import dev.engine.core.scene.AbstractScene;
import dev.engine.core.scene.camera.Camera;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.common.Renderer;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *     {@literal @}Override protected void update(float deltaTime) {
 *         scene().setLocalTransform(entity, Mat4.rotationY(time()));
 *     }
 * }
 *
 * // Launch:
 * new MyGame().run(config, toolkit, device);
 * </pre>
 */
public abstract class BaseApplication {

    private static final Logger log = LoggerFactory.getLogger(BaseApplication.class);

    private Engine engine;
    private WindowToolkit toolkit;
    private WindowHandle window;
    private InputState input;
    private Camera defaultCamera;

    /**
     * Launches with a toolkit + device factory.
     * The factory creates the toolkit and device, then this handles the rest.
     */
    public void launch(EngineConfig config, BackendFactory factory) {
        try {
            var backend = factory.create(config);
            this.toolkit = backend.toolkit();
            this.window = backend.window();
            runInternal(config, backend.device());
        } catch (Exception e) {
            log.error("Failed to launch application", e);
            throw new RuntimeException(e);
        }
    }

    /** Factory for creating backend-specific toolkit + device + window. */
    public interface BackendFactory {
        BackendInstance create(EngineConfig config);
    }

    public record BackendInstance(WindowToolkit toolkit, WindowHandle window, RenderDevice device) {}

    /**
     * Starts the application with explicit toolkit and render device.
     * Blocks until the window is closed.
     */
    public void run(EngineConfig config, WindowToolkit toolkit, RenderDevice device) {
        this.toolkit = toolkit;
        this.window = toolkit.createWindow(
                new dev.engine.graphics.window.WindowDescriptor(
                        config.windowTitle(), config.windowWidth(), config.windowHeight()));
        runInternal(config, device);
    }

    private void runInternal(EngineConfig config, RenderDevice device) {
        this.engine = new Engine(config, device);
        this.input = new InputState();

        // Default camera
        defaultCamera = engine.renderer().createCamera();

        window.show();

        try {
            init();

            log.info("Application started: {}", config.windowTitle());
            long lastTime = System.nanoTime();

            while (window.isOpen()) {
                long now = System.nanoTime();
                double delta = (now - lastTime) / 1_000_000_000.0;
                lastTime = now;

                toolkit.pollEvents();
                input.update();

                engine.renderer().setViewport(window.width(), window.height());
                float aspect = (float) window.width() / Math.max(window.height(), 1);

                update((float) delta);
                engine.tick(delta);
            }
        } finally {
            cleanup();
            engine.shutdown();
            toolkit.close();
            log.info("Application stopped");
        }
    }

    // --- Lifecycle methods (override these) ---

    /** Called once after engine initialization. Load assets, create entities here. */
    protected void init() {}

    /** Called every frame. Update game logic here. */
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
    protected InputState input() { return input; }
    protected Camera camera() { return defaultCamera; }
    protected WindowHandle window() { return window; }
    protected double time() { return engine.totalTime(); }
    protected long frameNumber() { return engine.frameNumber(); }

}
