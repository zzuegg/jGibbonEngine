package dev.engine.tests.screenshot;

import dev.engine.bindings.slang.SlangShaderCompiler;
import dev.engine.core.scene.SceneAccess;
import dev.engine.graphics.ScreenshotHelper;
import dev.engine.graphics.common.engine.Engine;
import dev.engine.graphics.common.engine.EngineConfig;
import dev.engine.graphics.common.engine.HeadlessPlatform;
import dev.engine.platform.desktop.DesktopPlatform;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Harness for screenshot testing using the full engine stack.
 * Creates a real Engine with EngineConfig, Platform, and GraphicsBackendFactory
 * — the same path as a real application.
 */
public class ScreenshotTestHarness {

    private final int width;
    private final int height;

    public ScreenshotTestHarness(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Renders a scene on the given backend and captures screenshots at the specified frames.
     * Returns a map of frame index → pixel data.
     */
    public Map<Integer, byte[]> render(RenderTestScene testScene, Backend backend) {
        var config = EngineConfig.builder()
                .windowTitle(backend.name() + " Test")
                .windowSize(width, height)
                .headless(false)
                .platform(DesktopPlatform.builder()
                        .shaderCompiler(new SlangShaderCompiler())
                        .build())
                .graphicsBackend(backend.factory())
                .maxFrames(0)
                .build();

        var windowDesc = new dev.engine.graphics.window.WindowDescriptor(
                config.windowTitle(), config.windowSize().x(), config.windowSize().y());
        var graphicsBackend = config.graphicsBackend().create(windowDesc);
        var engine = new Engine(config, config.platform(), graphicsBackend.device());

        try {
            engine.renderer().setViewport(width, height);
            testScene.setup(engine);

            int[] captureFrames = testScene.captureFrames();
            int maxFrame = 0;
            for (int f : captureFrames) maxFrame = Math.max(maxFrame, f);

            var captures = new HashMap<Integer, byte[]>();
            var captureSet = new java.util.HashSet<Integer>();
            for (int f : captureFrames) captureSet.add(f);

            for (int frame = 0; frame <= maxFrame; frame++) {
                engine.tick(1.0 / 60.0);

                if (captureSet.contains(frame)) {
                    byte[] pixels = graphicsBackend.device().readFramebuffer(width, height);
                    captures.put(frame, pixels);
                }
            }

            return captures;
        } finally {
            engine.shutdown();
            graphicsBackend.toolkit().close();
        }
    }

    /**
     * Renders a scene on the given backend and captures a single screenshot
     * (the last capture frame). Convenience for single-frame scenes.
     */
    public byte[] renderSingle(RenderTestScene testScene, Backend backend) {
        var captures = render(testScene, backend);
        int[] frames = testScene.captureFrames();
        return captures.get(frames[frames.length - 1]);
    }

    /** Saves a screenshot to build/screenshots/<backend>/<name>.png */
    public void saveScreenshot(byte[] pixels, String backend, String name) {
        try {
            var dir = new File("build/screenshots/" + backend);
            dir.mkdirs();
            ScreenshotHelper.save(pixels, width, height, dir.getPath() + "/" + name + ".png");
        } catch (IOException e) {
            System.err.println("Warning: failed to save screenshot " + backend + "/" + name + ": " + e.getMessage());
        }
    }

    /** Computes the difference percentage between two pixel arrays. */
    public static double diffPercent(byte[] a, byte[] b, int channelTolerance) {
        return ScreenshotHelper.diffPercentage(a, b, channelTolerance);
    }

    public int width() { return width; }
    public int height() { return height; }
}
