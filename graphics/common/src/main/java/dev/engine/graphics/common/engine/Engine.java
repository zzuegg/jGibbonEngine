package dev.engine.graphics.common.engine;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.handle.Handle;
import dev.engine.core.input.InputEvent;
import dev.engine.core.scene.MeshTag;
import dev.engine.core.mesh.MeshData;
import dev.engine.core.module.ModuleManager;
import dev.engine.core.module.Time;
import dev.engine.core.module.VariableTimestep;
import dev.engine.core.profiler.Profiler;
import dev.engine.core.profiler.RenderStats;
import dev.engine.core.scene.AbstractScene;
import dev.engine.core.scene.Scene;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.common.DebugUiOverlay;
import dev.engine.graphics.common.Renderer;
import dev.engine.ui.NkBuiltinFont;
import dev.engine.ui.NkContext;
import dev.engine.ui.NkFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// No Executors import — use Runnable::run for cross-platform compatibility

/**
 * The main engine class. Owns all core systems as a coordinated whole.
 *
 * <p>Systems:
 * <ul>
 *   <li>{@link ModuleManager} — user game logic modules</li>
 *   <li>{@link AssetManager} — asset loading, caching, hot reload</li>
 *   <li>{@link Renderer} — GPU rendering</li>
 *   <li>{@link AbstractScene} — the user's scene</li>
 * </ul>
 *
 * <p>In single-threaded mode, {@link #tick(double)} updates modules and renders.
 * In threaded mode, {@link #run()} starts the logic+render thread loop.
 */
public class Engine {

    private static final Logger log = LoggerFactory.getLogger(Engine.class);

    private final EngineConfig config;
    private final ModuleManager<Time> modules;
    private final AssetManager assets;
    private final Renderer renderer;
    private final AbstractScene scene;
    private final Profiler profiler;

    // Debug UI
    private final NkContext debugUi;
    private final DebugUiOverlay debugUiOverlay;

    // Threading
    private volatile boolean running = false;
    private Thread renderThread;

    // Time tracking
    private final java.util.concurrent.atomic.AtomicLong frameNumber = new java.util.concurrent.atomic.AtomicLong();
    private double totalTime = 0;

    // Input events for current frame (set before tick, read by modules)
    private java.util.List<InputEvent> currentInputEvents = java.util.List.of();

    public Engine(EngineConfig config, Platform platform, RenderDevice device) {
        this.config = config;

        // Scene
        this.scene = config.scene() != null ? config.scene() : new Scene();

        // Profiler
        this.profiler = new Profiler();

        // Asset manager — synchronous by default, works on all platforms
        this.assets = new AssetManager(Runnable::run);
        platform.configureAssets(assets);

        // Renderer — connected to asset manager for shader hot-reload
        this.renderer = new Renderer(device, platform.shaderCompiler());
        this.renderer.shaderManager().setAssetManager(assets);

        // Module manager — synchronous executor for cross-platform compatibility
        this.modules = new ModuleManager<>(new VariableTimestep<>(Time::new), Runnable::run);

        // Debug UI (skip if disabled)
        if (config.debugOverlay()) {
            NkFont uiFont = new NkBuiltinFont(2);
            this.debugUi = new NkContext(uiFont);
            this.debugUiOverlay = new DebugUiOverlay(device);
            this.debugUiOverlay.init(uiFont, this.renderer.shaderManager(), this.renderer.gpu());

            this.renderer.addPostSceneCallback(() -> {
                var vp = this.renderer.viewport();
                debugUiOverlay.render(debugUi, vp.width(), vp.height());
                debugUi.clear();
            });
        } else {
            this.debugUi = null;
            this.debugUiOverlay = new DebugUiOverlay(device); // no-op overlay (not initialized)
        }

        log.info("Engine initialized (headless={}, threaded={}, debugOverlay={})",
                config.headless(), config.threaded(), config.debugOverlay());
    }

    // --- Accessors ---

    public ModuleManager<Time> modules() { return modules; }
    public AssetManager assets() { return assets; }
    public Renderer renderer() { return renderer; }
    public AbstractScene scene() { return scene; }
    public Profiler profiler() { return profiler; }
    public RenderStats renderStats() { return renderer.renderStats(); }
    public EngineConfig config() { return config; }
    public NkContext debugUi() { return debugUi; }
    public DebugUiOverlay debugUiOverlay() { return debugUiOverlay; }
    public long frameNumber() { return frameNumber.get(); }
    public double totalTime() { return totalTime; }

    /** Returns the input events for the current frame. Set before each tick. */
    public java.util.List<InputEvent> inputEvents() { return currentInputEvents; }

    /** Sets the input events for the current frame. Called by the application loop. */
    public void setInputEvents(java.util.List<InputEvent> events) {
        this.currentInputEvents = events != null ? events : java.util.List.of();
    }

    // --- Resource creation (user-facing, no GPU concepts exposed) ---

    /**
     * Registers mesh data with the engine. Returns an opaque handle
     * for assignment to scene entities via {@code scene().setMesh(entity, handle)}.
     */
    public Handle<MeshTag> registerMesh(MeshData data) {
        return renderer.createMeshFromData(data);
    }

    // --- Single-threaded mode ---

    /**
     * Single tick: updates all modules, then renders.
     * Use this in a custom game loop or for testing.
     */
    public void tick(double deltaSeconds) {
        profiler.newFrame();
        totalTime += deltaSeconds;

        try (var scope = profiler.scope("logic")) {
            modules.tick(deltaSeconds);
        }

        try (var scope = profiler.scope("render")) {
            var transactions = dev.engine.core.scene.SceneAccess.drainTransactions(scene);
            renderer.renderFrame(transactions);
        }

        frameNumber.incrementAndGet();
    }

    // --- Threaded mode ---

    /**
     * Starts the engine loop with separate logic and render threads.
     * Blocks until {@link #stop()} is called.
     */
    public void run() {
        if (!config.threaded()) {
            throw new IllegalStateException("Engine not configured for threaded mode");
        }

        running = true;

        // Render thread
        renderThread = Thread.ofPlatform().name("render").start(() -> {
            log.info("Render thread started");
            while (running) {
                var transactions = dev.engine.core.scene.SceneAccess.drainTransactions(scene);
                renderer.renderFrame(transactions);
                frameNumber.incrementAndGet();
            }
            log.info("Render thread stopped");
        });

        // Logic thread (this thread)
        log.info("Logic thread started");
        long lastTime = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            double delta = (now - lastTime) / 1_000_000_000.0;
            lastTime = now;

            modules.tick(delta);

            // Don't spin — yield if we're faster than 1000 Hz
            if (delta < 0.001) {
                Thread.onSpinWait();
            }
        }
    }

    /** Stops the threaded engine loop. */
    public void stop() {
        running = false;
        if (renderThread != null) {
            try { renderThread.join(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    // --- Shutdown ---

    public void shutdown() {
        stop();
        modules.shutdown();
        assets.shutdown();
        debugUiOverlay.close();
        renderer.close();
        log.info("Engine shut down");
    }

}
