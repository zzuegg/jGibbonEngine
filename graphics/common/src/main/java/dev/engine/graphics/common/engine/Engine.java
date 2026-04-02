package dev.engine.graphics.common.engine;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.FileSystemAssetSource;
import dev.engine.core.asset.ImageLoader;
import dev.engine.core.handle.Handle;
import dev.engine.core.scene.MeshTag;
import dev.engine.core.scene.MaterialTag;
import dev.engine.graphics.common.material.Material;
import dev.engine.graphics.common.material.MaterialType;
import dev.engine.graphics.mesh.MeshData;
import dev.engine.core.module.ModuleManager;
import dev.engine.core.module.Time;
import dev.engine.core.module.VariableTimestep;
import dev.engine.core.profiler.Profiler;
import dev.engine.core.profiler.RenderStats;
import dev.engine.core.scene.AbstractScene;
import dev.engine.core.scene.Scene;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.common.HeadlessRenderDevice;
import dev.engine.graphics.common.Renderer;
import dev.engine.graphics.mesh.ObjLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.Executors;

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
    private final RenderStats renderStats;

    // Threading
    private volatile boolean running = false;
    private Thread renderThread;

    // Time tracking
    private long frameNumber = 0;
    private double totalTime = 0;

    public Engine(EngineConfig config) {
        this(config, config.headless() ? new HeadlessRenderDevice() : null);
    }

    public Engine(EngineConfig config, RenderDevice device) {
        this.config = config;

        // Scene
        this.scene = config.scene() != null ? config.scene() : new Scene();

        // Profiler + stats
        this.profiler = new Profiler();
        this.renderStats = new RenderStats();

        // Asset manager with default loaders
        this.assets = new AssetManager(Executors.newSingleThreadExecutor());
        assets.addSource(new FileSystemAssetSource(Path.of("assets")));
        assets.registerLoader(new ImageLoader());
        assets.registerLoader(new ObjLoader());

        // Renderer
        if (device == null) device = new HeadlessRenderDevice();
        this.renderer = new Renderer(device, scene);

        // Module manager
        this.modules = new ModuleManager<>(new VariableTimestep<>(Time::new), Executors.newWorkStealingPool());

        log.info("Engine initialized (headless={}, threaded={})", config.headless(), config.threaded());
    }

    // --- Accessors ---

    public ModuleManager<Time> modules() { return modules; }
    public AssetManager assets() { return assets; }
    public Renderer renderer() { return renderer; }
    public AbstractScene scene() { return scene; }
    public Profiler profiler() { return profiler; }
    public RenderStats renderStats() { return renderStats; }
    public EngineConfig config() { return config; }
    public long frameNumber() { return frameNumber; }
    public double totalTime() { return totalTime; }

    // --- Resource creation (user-facing, no GPU concepts exposed) ---

    /**
     * Registers mesh data with the engine. Returns an opaque handle
     * for assignment to scene entities via {@code scene().setMesh(entity, handle)}.
     */
    public Handle<MeshTag> registerMesh(MeshData data) {
        return renderer.createMeshFromData(data);
    }

    /**
     * Creates a material and returns an opaque handle.
     * Use {@code material(handle)} to set properties.
     * Assign to entities via {@code scene().setMaterial(entity, handle)}.
     */
    public Handle<MaterialTag> createMaterial(MaterialType type) {
        return renderer.createMaterial(type);
    }

    /** Gets the Material object for property setting. */
    public Material material(Handle<MaterialTag> handle) {
        return renderer.material(handle);
    }

    // --- Single-threaded mode ---

    /**
     * Single tick: updates all modules, then renders.
     * Use this in a custom game loop or for testing.
     */
    public void tick(double deltaSeconds) {
        profiler.newFrame();
        renderStats.reset();
        totalTime += deltaSeconds;

        try (var scope = profiler.scope("logic")) {
            modules.tick(deltaSeconds);
        }

        try (var scope = profiler.scope("render")) {
            renderer.setViewport(config.windowWidth(), config.windowHeight());
            renderer.renderFrame();
        }

        frameNumber++;
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
                renderer.renderFrame();
                frameNumber++;
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
        renderer.close();
        log.info("Engine shut down");
    }
}
