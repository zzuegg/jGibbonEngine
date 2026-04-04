package dev.engine.examples;

import dev.engine.bindings.sdl3.Sdl3InputProvider;
import dev.engine.bindings.sdl3.Sdl3WindowToolkit;
import dev.engine.core.input.*;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.Entity;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.GraphicsBackendFactory;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.opengl.OpenGlBackend;
import dev.engine.graphics.vulkan.VulkanBackend;
import dev.engine.graphics.webgpu.WebGpuBackend;
import dev.engine.graphics.window.WindowToolkit;
import dev.engine.platform.desktop.DesktopPlatform;
import dev.engine.providers.jwebgpu.JWebGpuBindings;
import dev.engine.providers.lwjgl.graphics.vulkan.LwjglVkBindings;
import dev.engine.windowing.glfw.GlfwInputProvider;
import dev.engine.windowing.glfw.GlfwWindowToolkit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A small game to test the input system.
 *
 * WASD to move the player cube. Space to shoot a projectile forward.
 * Mouse click spawns a target at a random position. Scroll to change player color.
 * ESC to quit.
 */
public class InputGameExample extends BaseApplication {

    private Entity player;
    private float playerX = 0, playerZ = 0;
    private float playerAngle = 0;
    private int colorIndex = 0;

    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<Entity> targets = new ArrayList<>();
    private final java.util.Set<KeyCode> heldKeys = java.util.EnumSet.noneOf(KeyCode.class);

    private static final Vec3[] COLORS = {
        new Vec3(0.2f, 0.6f, 1.0f),  // blue
        new Vec3(1.0f, 0.3f, 0.2f),  // red
        new Vec3(0.2f, 1.0f, 0.3f),  // green
        new Vec3(1.0f, 0.9f, 0.1f),  // yellow
        new Vec3(0.8f, 0.2f, 0.9f),  // purple
    };

    record Projectile(Entity entity, float x, float z, float dx, float dz, float life) {}

    private static WindowToolkit sharedToolkit;
    private static Sdl3InputProvider sdl3InputProvider;

    @Override
    protected InputProvider createInputProvider() {
        if (window() instanceof GlfwWindowToolkit.GlfwWindowHandle glfwWindow) {
            return new GlfwInputProvider(glfwWindow);
        }
        if (sdl3InputProvider != null) {
            return sdl3InputProvider;
        }
        return null;
    }

    @Override
    protected void init() {
        // Verify input system is wired
        var mouse = inputSystem().mouse(0);
        System.out.println("[InputGame] Mouse device: " + mouse.name() + ", cursor: " + mouse.cursorMode());
        var kb = inputSystem().keyboard(0);
        System.out.println("[InputGame] Keyboard device: " + kb.name());

        // Player cube
        player = scene().createEntity();
        player.add(PrimitiveMeshes.cube());
        player.add(MaterialData.unlit(COLORS[colorIndex]));
        player.add(Transform.at(0, 0.5f, 0));

        // Ground plane
        var ground = scene().createEntity();
        ground.add(PrimitiveMeshes.plane(20, 20));
        ground.add(MaterialData.unlit(new Vec3(0.15f, 0.15f, 0.2f)));
        ground.add(Transform.at(0, 0, 0).withScale(20f));

        // Initial targets
        for (int i = 0; i < 5; i++) {
            spawnTarget();
        }

        camera().lookAt(new Vec3(0, 12, 10), new Vec3(0, 0, 0), Vec3.UNIT_Y);
        camera().setPerspective((float) Math.toRadians(60), 16f / 9f, 0.1f, 100f);
    }

    @Override
    protected void update(float dt, List<InputEvent> events) {
        float speed = 6f * dt;
        float rotSpeed = 3f * dt;

        // Track key state and handle actions from events (works with any provider)
        for (var event : events) {
            switch (event) {
                case InputEvent.KeyPressed e -> {
                    heldKeys.add(e.keyCode());
                    if (e.keyCode() == KeyCode.SPACE) shoot();
                }
                case InputEvent.KeyReleased e -> heldKeys.remove(e.keyCode());
                case InputEvent.Scrolled e -> {
                    colorIndex = (colorIndex + (e.y() > 0 ? 1 : COLORS.length - 1)) % COLORS.length;
                    player.add(MaterialData.unlit(COLORS[colorIndex]));
                }
                case InputEvent.MousePressed e when e.button() == MouseButton.LEFT -> spawnTarget();
                default -> {}
            }
        }

        boolean moveForward = heldKeys.contains(KeyCode.W);
        boolean moveBack = heldKeys.contains(KeyCode.S);
        boolean turnLeft = heldKeys.contains(KeyCode.A);
        boolean turnRight = heldKeys.contains(KeyCode.D);

        // Turn
        if (turnLeft) playerAngle += rotSpeed;
        if (turnRight) playerAngle -= rotSpeed;

        // Move in facing direction
        float dx = -(float) Math.sin(playerAngle);
        float dz = -(float) Math.cos(playerAngle);
        if (moveForward) { playerX += dx * speed; playerZ += dz * speed; }
        if (moveBack) { playerX -= dx * speed; playerZ -= dz * speed; }

        // Clamp to arena
        playerX = Math.clamp(playerX, -9f, 9f);
        playerZ = Math.clamp(playerZ, -9f, 9f);

        player.add(Transform.at(playerX, 0.5f, playerZ).rotatedY(playerAngle));

        // Update camera to follow player
        float camX = playerX - dx * 10;
        float camZ = playerZ - dz * 10;
        camera().lookAt(new Vec3(camX, 8, camZ), new Vec3(playerX, 0, playerZ), Vec3.UNIT_Y);
        float aspect = (float) window().width() / Math.max(window().height(), 1);
        camera().setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);

        // Update projectiles
        updateProjectiles(dt);
    }

    private void shoot() {
        float dx = -(float) Math.sin(playerAngle);
        float dz = -(float) Math.cos(playerAngle);

        var bullet = scene().createEntity();
        bullet.add(PrimitiveMeshes.sphere());
        bullet.add(MaterialData.unlit(COLORS[colorIndex]));
        bullet.add(Transform.at(playerX, 0.5f, playerZ).withScale(0.3f));

        projectiles.add(new Projectile(bullet, playerX, playerZ, dx * 15, dz * 15, 2f));
    }

    private void spawnTarget() {
        float tx = (float) (Math.random() * 16 - 8);
        float tz = (float) (Math.random() * 16 - 8);
        var target = scene().createEntity();
        target.add(PrimitiveMeshes.cube());
        target.add(MaterialData.unlit(new Vec3(1f, 0.2f, 0.2f)));
        target.add(Transform.at(tx, 0.5f, tz).withScale(0.6f));
        targets.add(target);
    }

    private void updateProjectiles(float dt) {
        for (int i = projectiles.size() - 1; i >= 0; i--) {
            var p = projectiles.get(i);
            float nx = p.x() + p.dx() * dt;
            float nz = p.z() + p.dz() * dt;
            float life = p.life() - dt;

            if (life <= 0 || Math.abs(nx) > 10 || Math.abs(nz) > 10) {
                scene().destroyEntity(p.entity());
                projectiles.remove(i);
                continue;
            }

            // Check target collisions
            boolean hit = false;
            for (int t = targets.size() - 1; t >= 0; t--) {
                var target = targets.get(t);
                var tPos = target.get(Transform.class);
                if (tPos != null) {
                    float tdx = tPos.position().x() - nx;
                    float tdz = tPos.position().z() - nz;
                    if (tdx * tdx + tdz * tdz < 0.8f) {
                        scene().destroyEntity(target);
                        targets.remove(t);
                        hit = true;
                        spawnTarget();
                        break;
                    }
                }
            }

            if (hit) {
                scene().destroyEntity(p.entity());
                projectiles.remove(i);
            } else {
                p.entity().add(Transform.at(nx, 0.5f, nz).withScale(0.3f));
                projectiles.set(i, new Projectile(p.entity(), nx, nz, p.dx(), p.dz(), life));
            }
        }
    }

    public static void main(String[] args) {
        String windowing = System.getProperty("engine.windowing", "glfw");
        String backend = System.getProperty("engine.backend", "opengl");

        WindowToolkit toolkit;
        if ("sdl3".equals(windowing)) {
            var sdl3Toolkit = new Sdl3WindowToolkit("opengl".equals(backend));
            sdl3InputProvider = new Sdl3InputProvider();
            sdl3Toolkit.setInputProvider(sdl3InputProvider);
            toolkit = sdl3Toolkit;
        } else {
            var hints = "opengl".equals(backend)
                    ? GlfwWindowToolkit.OPENGL_HINTS
                    : GlfwWindowToolkit.NO_API_HINTS;
            toolkit = new GlfwWindowToolkit(hints);
        }
        sharedToolkit = toolkit;

        GraphicsBackendFactory graphicsBackend = switch (backend) {
            case "vulkan" -> VulkanBackend.factory(toolkit, new VulkanBackend.SurfaceCreator() {
                public String[] requiredInstanceExtensions() {
                    return GlfwWindowToolkit.getRequiredVulkanExtensions();
                }
                public long createSurface(long instance, long windowHandle) {
                    return GlfwWindowToolkit.createVulkanSurfaceFromHandle(instance, windowHandle);
                }
            }, new LwjglVkBindings());
            case "webgpu" -> {
                var gpu = new JWebGpuBindings();
                gpu.enableSurface(true);
                yield WebGpuBackend.factory(toolkit, gpu);
            }
            default -> OpenGlBackend.factory(toolkit, new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings());
        };

        var config = EngineConfig.builder()
                .windowTitle("Input Game (" + windowing + "/" + backend + ") - WASD move, Space shoot, Scroll color, Click spawn")
                .windowSize(1280, 720)
                .maxFrames(0)
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(graphicsBackend)
                .build();

        new InputGameExample().launch(config);
    }
}
