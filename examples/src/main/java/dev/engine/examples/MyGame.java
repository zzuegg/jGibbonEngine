package dev.engine.examples;

import dev.engine.core.input.*;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.Entity;
import dev.engine.core.scene.component.Transform;
import dev.engine.graphics.common.engine.BaseApplication;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.bindings.sdl3.Sdl3WindowToolkit;
import dev.engine.graphics.GraphicsBackendFactory;
import dev.engine.graphics.opengl.OpenGlBackend;
import dev.engine.graphics.vulkan.VulkanBackend;
import dev.engine.graphics.webgpu.WebGpuBackend;
import dev.engine.graphics.window.WindowToolkit;
import dev.engine.platform.desktop.DesktopPlatform;
import dev.engine.windowing.glfw.GlfwInputProvider;
import dev.engine.windowing.glfw.GlfwWindowToolkit;
import dev.engine.providers.jwebgpu.JWebGpuBindings;
import dev.engine.providers.lwjgl.graphics.vulkan.LwjglVkBindings;

import java.util.List;

public class MyGame extends BaseApplication {

    private Entity root, cube1, cube2, sphere, plane;

    @Override
    protected void init() {
        root = scene().createEntity();
        root.add(Transform.IDENTITY);

        // Red metallic cube
        cube1 = scene().createEntity();
        cube1.setParent(root);
        cube1.add(PrimitiveMeshes.cube());
        cube1.add(MaterialData.pbr(new Vec3(0.9f, 0.2f, 0.2f), 0.3f, 0.8f));
        cube1.add(Transform.at(-2, 0.5f, 0));

        // Green rough cube
        cube2 = scene().createEntity();
        cube2.setParent(root);
        cube2.add(PrimitiveMeshes.cube());
        cube2.add(MaterialData.pbr(new Vec3(0.2f, 0.9f, 0.2f), 0.9f, 0.1f));
        cube2.add(Transform.at(2, 0.5f, 0));

        // Blue unlit sphere
        sphere = scene().createEntity();
        sphere.setParent(root);
        sphere.add(PrimitiveMeshes.sphere());
        sphere.add(MaterialData.unlit(new Vec3(0.3f, 0.3f, 1.0f)));
        sphere.add(Transform.at(0, 1.5f, 0));

        // Ground plane
        plane = scene().createEntity();
        plane.add(PrimitiveMeshes.plane(10, 10));
        plane.add(MaterialData.pbr(new Vec3(0.4f, 0.4f, 0.4f), 0.8f, 0f));
        plane.add(Transform.at(0, 0, 0).withScale(10f));

        camera().lookAt(new Vec3(0, 4, 8), new Vec3(0, 1, 0), Vec3.UNIT_Y);
    }

    @Override
    protected InputProvider createInputProvider() {
        if (window() instanceof GlfwWindowToolkit.GlfwWindowHandle glfwWindow) {
            return new GlfwInputProvider(glfwWindow);
        }
        return null;
    }

    @Override
    protected void update(float dt, List<InputEvent> inputEvents) {
        for (var event : inputEvents) {
            switch (event) {
                case InputEvent.KeyPressed e when e.keyCode() == KeyCode.ESCAPE -> {
                    // Close handled by window
                }
                default -> {}
            }
        }

        float t = (float) time();
        float aspect = (float) window().width() / Math.max(window().height(), 1);
        camera().setPerspective((float) Math.toRadians(60), aspect, 0.1f, 100f);

        root.update(Transform.class, tr -> tr.rotatedY(dt * 0.3f));
        cube1.update(Transform.class, tr -> tr.withPosition(-2, 0.5f, 0).rotatedX(dt));
        cube2.update(Transform.class, tr -> tr.withPosition(2, 0.5f, 0).rotatedY(dt * 2));
        sphere.update(Transform.class, tr -> tr.withPosition(0, 1.5f + (float) Math.sin(t) * 0.5f, 0));
    }

    public static void main(String[] args) {
        String backend = System.getProperty("engine.backend", "opengl");
        String windowing = System.getProperty("engine.windowing", "glfw");

        WindowToolkit toolkit;
        if ("sdl3".equals(windowing)) {
            toolkit = new Sdl3WindowToolkit("opengl".equals(backend));
        } else {
            var hints = "opengl".equals(backend)
                    ? GlfwWindowToolkit.OPENGL_HINTS
                    : GlfwWindowToolkit.NO_API_HINTS;
            toolkit = new GlfwWindowToolkit(hints);
        }

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
                .windowTitle("My Game (" + windowing + "/" + backend + ")")
                .windowSize(1280, 720)
                .maxFrames(Integer.getInteger("engine.maxFrames", 0))
                .platform(DesktopPlatform.builder().build())
                .graphicsBackend(graphicsBackend)
                .build();

        new MyGame().launch(config);
    }
}
