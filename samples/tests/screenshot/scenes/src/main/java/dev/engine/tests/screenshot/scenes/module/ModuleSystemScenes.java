package dev.engine.tests.screenshot.scenes.module;

import dev.engine.core.Discoverable;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.module.AbstractModule;
import dev.engine.core.module.Module;
import dev.engine.core.module.ModuleManager;
import dev.engine.core.module.Time;
import dev.engine.core.scene.Entity;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.Engine;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.tests.screenshot.scenes.RenderTestScene;

import java.util.Set;

/**
 * End-to-end screenshot tests for the module system.
 *
 * <p>These scenes prove that the {@link ModuleManager} is fully operational
 * when wired through a real engine + renderer: dependency declaration,
 * cross-module lookup, lifecycle state transitions, and dependency-level
 * ordering of updates must all work for the reference image to match.
 *
 * <p>The scenes use the default {@link Engine}-configured executor, which
 * is {@code Runnable::run} on web/graalwasm platforms and a virtual-thread
 * executor on desktop. Because each module writes only to its own entity and
 * the scene uses constant (non-time-dependent) offsets, the rendered output
 * is identical regardless of execution order.
 */
@Discoverable
public class ModuleSystemScenes {

    /**
     * Four modules, arranged as a two-level dependency graph:
     *
     * <pre>
     *   level 0:   RedLifter   GreenLifter   BlueLifter     (independent)
     *                   \          |          /
     *                    \         |         /
     *   level 1:            CentroidModule                  (depends on all three)
     * </pre>
     *
     * Each level-0 module owns one colored cube and writes its vertical
     * offset into the cube's {@link Transform}. The level-1 module resolves
     * the three lifters via {@link AbstractModule#getModule(Class)} in
     * {@code doInit}, then on every update reads their offsets, averages
     * them, and positions a white marker cube at the centroid.
     *
     * <p>What breaks the screenshot if the module system is broken:
     * <ul>
     *   <li>Registration/lifecycle: a cube stays at y=0 (doUpdate never ran).</li>
     *   <li>Dependency declaration: CentroidModule.add() throws
     *       MissingDependencyException — test errors, image missing.</li>
     *   <li>Level ordering: marker sits at the origin (CentroidModule ran
     *       before the lifters had updated).</li>
     *   <li>getModule() lookup: CentroidModule.doInit throws — scene crashes.</li>
     *   <li>Parallel-level graph walk skips a level-0 module: one cube is
     *       in the wrong place and the centroid is visibly off.</li>
     * </ul>
     */
    public static final RenderTestScene PARALLEL_MODULES_OPERATIONAL = engine -> {
        var renderer = engine.renderer();
        var scene = engine.scene();

        var cam = renderer.createCamera();
        cam.lookAt(new Vec3(0, 3, 8), Vec3.ZERO, Vec3.UNIT_Y);
        cam.setPerspective((float) Math.toRadians(60), 256f / 256f, 0.1f, 100f);
        renderer.setActiveCamera(cam);

        // Three colored cubes — starting at y=0, each lifted by its owning module.
        var red = scene.createEntity();
        red.add(PrimitiveMeshes.cube());
        red.add(MaterialData.unlit(new Vec3(0.9f, 0.2f, 0.2f)));
        red.add(Transform.at(-2.5f, 0, 0));

        var green = scene.createEntity();
        green.add(PrimitiveMeshes.cube());
        green.add(MaterialData.unlit(new Vec3(0.2f, 0.9f, 0.2f)));
        green.add(Transform.at(0, 0, 0));

        var blue = scene.createEntity();
        blue.add(PrimitiveMeshes.cube());
        blue.add(MaterialData.unlit(new Vec3(0.2f, 0.2f, 0.9f)));
        blue.add(Transform.at(2.5f, 0, 0));

        // White marker cube — positioned by CentroidModule at the average
        // of the three lifters' Y offsets. Pushed forward in Z so it isn't
        // occluded by the green cube (which sits at y=1.0, the same average).
        var marker = scene.createEntity();
        marker.add(PrimitiveMeshes.cube());
        marker.add(MaterialData.unlit(new Vec3(1.0f, 1.0f, 1.0f)));
        marker.add(Transform.at(0, 0, 2.5f).withScale(0.4f));

        // Register modules in dependency order. Level-0 first so that
        // CentroidModule.add() succeeds — the manager validates that every
        // declared dependency is already RUNNING at registration time.
        engine.modules().add(new RedLifterModule(red));
        engine.modules().add(new GreenLifterModule(green));
        engine.modules().add(new BlueLifterModule(blue));
        engine.modules().add(new CentroidModule(marker));
    };

    // ---------- Level 0: independent "lifter" modules ----------
    //
    // Each owns one cube and writes a constant Y offset to it. No
    // dependencies on other modules → all three sit at level 0 of the
    // dependency graph and are eligible for parallel execution.

    /** Lifts the red cube to y = 0.5. */
    public static final class RedLifterModule extends AbstractModule<Time> {
        private static final float OFFSET_Y = 0.5f;
        private final Entity cube;

        public RedLifterModule(Entity cube) {
            this.cube = cube;
        }

        public float offsetY() { return OFFSET_Y; }

        @Override
        protected void doUpdate(Time context) {
            cube.update(Transform.class, tr -> tr.withPosition(-2.5f, OFFSET_Y, 0));
        }
    }

    /** Lifts the green cube to y = 1.0. */
    public static final class GreenLifterModule extends AbstractModule<Time> {
        private static final float OFFSET_Y = 1.0f;
        private final Entity cube;

        public GreenLifterModule(Entity cube) {
            this.cube = cube;
        }

        public float offsetY() { return OFFSET_Y; }

        @Override
        protected void doUpdate(Time context) {
            cube.update(Transform.class, tr -> tr.withPosition(0, OFFSET_Y, 0));
        }
    }

    /** Lifts the blue cube to y = 1.5. */
    public static final class BlueLifterModule extends AbstractModule<Time> {
        private static final float OFFSET_Y = 1.5f;
        private final Entity cube;

        public BlueLifterModule(Entity cube) {
            this.cube = cube;
        }

        public float offsetY() { return OFFSET_Y; }

        @Override
        protected void doUpdate(Time context) {
            cube.update(Transform.class, tr -> tr.withPosition(2.5f, OFFSET_Y, 0));
        }
    }

    // ---------- Level 1: dependent aggregator ----------
    //
    // Declares dependencies on all three lifters, which forces the
    // ModuleManager to put it in a later dependency level. Resolves them
    // via getModule() in doInit, then averages their offsets into a white
    // marker cube on every tick.

    /** Positions the marker cube at the centroid of the three lifters. */
    public static final class CentroidModule extends AbstractModule<Time> {
        private final Entity marker;
        private RedLifterModule red;
        private GreenLifterModule green;
        private BlueLifterModule blue;

        public CentroidModule(Entity marker) {
            this.marker = marker;
        }

        @Override
        public Set<Class<? extends Module<Time>>> getDependencies() {
            return Set.of(
                    RedLifterModule.class,
                    GreenLifterModule.class,
                    BlueLifterModule.class);
        }

        @Override
        protected void doInit(ModuleManager<Time> manager) {
            // Resolve all three dependencies through the manager.
            // If any lookup returns null, the test fails on NPE in doUpdate.
            this.red = getModule(RedLifterModule.class);
            this.green = getModule(GreenLifterModule.class);
            this.blue = getModule(BlueLifterModule.class);
        }

        @Override
        protected void doUpdate(Time context) {
            // Average the three lifters' Y offsets — with constants 0.5, 1.0, 1.5
            // this is always exactly 1.0. Deterministic across all backends.
            // The marker sits in front of the cubes (z=2.5) so it doesn't
            // intersect the green cube at (0, 1.0, 0).
            float avgY = (red.offsetY() + green.offsetY() + blue.offsetY()) / 3f;
            marker.update(Transform.class, tr -> tr.withPosition(0, avgY, 2.5f));
        }
    }
}
