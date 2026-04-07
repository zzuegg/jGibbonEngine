package dev.engine.tests.screenshot.desktop;

import dev.engine.graphics.GraphicsBackend;
import dev.engine.graphics.GraphicsBackendFactory;
import dev.engine.graphics.GraphicsConfigLegacy;
import dev.engine.graphics.ScreenshotHelper;
import dev.engine.graphics.common.engine.Engine;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.vulkan.VkRenderDevice;
import dev.engine.graphics.vulkan.VulkanBackend;
import dev.engine.graphics.webgpu.WgpuRenderDevice;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.platform.desktop.DesktopPlatform;
import dev.engine.providers.jwebgpu.JWebGpuBindings;
import dev.engine.providers.lwjgl.graphics.vulkan.LwjglVkBindings;
import dev.engine.tests.screenshot.scenes.RenderTestScene;
import dev.engine.windowing.glfw.GlfwWindowToolkit;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Child process entry point for rendering a single scene on a single backend.
 *
 * <p>Args: {@code <className> <fieldName> <backend> <outputDir> <resultFile>}
 *
 * <p>Loads the scene via reflection, creates the backend, renders frames,
 * saves screenshots, and writes a {@link ChildResult} JSON file.
 */
public class DesktopRenderMain {

    public static void main(String[] args) {
        String className = args[0];
        String fieldName = args[1];
        String backend = args[2];
        Path outputDir = Path.of(args[3]);
        Path resultFile = Path.of(args[4]);

        try {
            var clazz = Class.forName(className);
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            var scene = (RenderTestScene) field.get(null);
            var config = scene.config();

            // Scene provides a partially-filled builder; runner adds platform/backend
            var factory = createBackendFactory(backend);
            var platform = DesktopPlatform.builder().build();
            var engineConfig = config.engineConfigBuilder()
                    .platform(platform)
                    .graphicsBackend(factory)
                    .build();
            var windowDesc = engineConfig.window();
            var gfxConfig = new GraphicsConfigLegacy(false);
            var gfxBackend = factory.create(windowDesc, gfxConfig);
            var engine = new Engine(engineConfig, platform, gfxBackend.device());

            try {
                engine.renderer().setViewport(config.width(), config.height());
                scene.setup(engine);

                int maxFrame = config.captureFrames().stream()
                        .mapToInt(Integer::intValue).max().orElse(3);
                var inputScript = scene.inputScript();
                var captures = new ArrayList<ChildResult.FrameCapture>();

                for (int frame = 0; frame <= maxFrame; frame++) {
                    engine.setInputEvents(inputScript.getOrDefault(frame, List.of()));
                    engine.tick(1.0 / 60.0);

                    if (config.captureFrames().contains(frame)) {
                        byte[] pixels = gfxBackend.device().readFramebuffer(
                                config.width(), config.height());
                        var sceneName = fieldName.toLowerCase();
                        var filename = sceneName + "_f" + frame + ".png";
                        var dir = outputDir.resolve(backend);
                        Files.createDirectories(dir);
                        ScreenshotHelper.save(pixels, config.width(), config.height(),
                                dir.resolve(filename).toString());
                        captures.add(new ChildResult.FrameCapture(frame,
                                backend + "/" + filename));
                    }
                }

                ChildResult.success(captures).writeTo(resultFile);
            } finally {
                engine.shutdown();
                gfxBackend.toolkit().close();
            }
        } catch (Exception e) {
            try {
                ChildResult.exception(e.getMessage(), stackTraceToString(e)).writeTo(resultFile);
            } catch (Exception ignored) {}
            System.exit(1);
        }
    }

    @SuppressWarnings("deprecation")
    private static GraphicsBackendFactory createBackendFactory(String backend) {
        return switch (backend) {
            case "opengl" -> (windowDesc, config) -> {
                var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
                var window = toolkit.createWindow(windowDesc);
                var device = new GlRenderDevice(window,
                        new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings());
                return new GraphicsBackend(toolkit, window, device);
            };
            case "vulkan" -> (windowDesc, config) -> {
                var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
                var window = toolkit.createWindow(windowDesc);
                var device = new VkRenderDevice(
                        new LwjglVkBindings(),
                        GlfwWindowToolkit.getRequiredVulkanExtensions(),
                        instance -> GlfwWindowToolkit.createVulkanSurfaceFromHandle(
                                instance, window.nativeHandle()),
                        windowDesc.width(), windowDesc.height());
                return new GraphicsBackend(toolkit, window, device);
            };
            case "webgpu" -> (windowDesc, config) -> {
                var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
                var window = toolkit.createWindow(windowDesc);
                var device = new WgpuRenderDevice(window, new JWebGpuBindings());
                return new GraphicsBackend(toolkit, window, device);
            };
            default -> throw new IllegalArgumentException("Unknown backend: " + backend);
        };
    }

    private static String stackTraceToString(Exception e) {
        var sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
