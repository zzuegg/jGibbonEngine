package dev.engine.examples;

import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.ScreenshotHelper;
import dev.engine.graphics.common.Renderer;
import dev.engine.graphics.opengl.GlRenderDevice;
import dev.engine.graphics.vulkan.VkRenderDevice;
import dev.engine.graphics.webgpu.WgpuRenderDevice;
import dev.engine.windowing.glfw.GlfwWindowToolkit;
import dev.engine.graphics.window.WindowDescriptor;
import org.lwjgl.glfw.GLFWVulkan;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reusable test harness for cross-backend visual regression testing.
 *
 * <p>Renders a {@link RenderTestScene} on both backends, compares the output,
 * and verifies against stored reference screenshots.
 *
 * <pre>{@code
 * var harness = new RenderTestHarness(256, 256);
 * harness.assertMatchesReference(MY_SCENE, "my_scene");
 * harness.assertBackendsMatch(MY_SCENE);
 * }</pre>
 */
public class RenderTestHarness {

    /** Tolerance preset for pixel comparison. */
    public record Tolerance(int maxChannelDiff, double maxDiffPercent) {
        /** Exact match — zero tolerance. */
        public static Tolerance exact() { return new Tolerance(0, 0.0); }
        /** Tight tolerance — allows for minor rounding differences. */
        public static Tolerance tight() { return new Tolerance(1, 0.001); }
        /** Loose tolerance — allows for cross-backend variation. */
        public static Tolerance loose() { return new Tolerance(5, 0.05); }
    }

    private final int width;
    private final int height;
    private final int channelTolerance;
    private final double maxDiffPercent;

    public RenderTestHarness(int width, int height) {
        this(width, height, 30, 10.0);
    }

    public RenderTestHarness(int width, int height, int channelTolerance, double maxDiffPercent) {
        this.width = width;
        this.height = height;
        this.channelTolerance = channelTolerance;
        this.maxDiffPercent = maxDiffPercent;
    }

    /** Renders the scene on both backends and asserts they match using default tolerance. */
    public void assertBackendsMatch(RenderTestScene scene) {
        assertBackendsMatch(scene, new Tolerance(channelTolerance, maxDiffPercent));
    }

    /** Renders the scene on both backends and asserts they match using the given tolerance. */
    public void assertBackendsMatch(RenderTestScene scene, Tolerance tolerance) {
        byte[] gl = renderOpenGl(scene);
        byte[] vk = renderVulkan(scene);
        saveScreenshot(gl, "opengl", "backends_match");
        saveScreenshot(vk, "vulkan", "backends_match");

        double diffPct = ScreenshotHelper.diffPercentage(gl, vk, tolerance.maxChannelDiff());
        assertTrue(diffPct < tolerance.maxDiffPercent(),
                "Backends differ by " + String.format("%.1f%%", diffPct)
                        + " (max " + tolerance.maxDiffPercent() + "%). "
                        + "Screenshots in build/screenshots/");
    }

    /**
     * Renders on both backends, compares with the given tolerance, and saves screenshots.
     * Use this for cross-backend comparison tests with explicit tolerance control.
     * WebGPU is included when available but does not fail tests if missing.
     */
    public void assertCrossBackend(RenderTestScene scene, String name, Tolerance tolerance) {
        byte[] gl = renderOpenGl(scene);
        byte[] vk = renderVulkan(scene);
        saveScreenshot(gl, "opengl", name);
        saveScreenshot(vk, "vulkan", name);

        double diffPct = ScreenshotHelper.diffPercentage(gl, vk, tolerance.maxChannelDiff());
        assertTrue(diffPct < tolerance.maxDiffPercent(),
                "Cross-backend '" + name + "': GL/VK differ by "
                        + String.format("%.1f%%", diffPct)
                        + " (max " + tolerance.maxDiffPercent() + "%). "
                        + "Screenshots in build/screenshots/opengl/" + name + ".png and build/screenshots/vulkan/" + name + ".png");

        // WebGPU comparison — wider tolerance (different rasterization rules, Y-flip, etc.)
        byte[] wgpu = renderWebGpu(scene);
        if (wgpu != null) {
            saveScreenshot(wgpu, "webgpu", name);
            var wgpuTolerance = new Tolerance(
                    Math.max(tolerance.maxChannelDiff(), 5),
                    Math.max(tolerance.maxDiffPercent(), 5.0));
            double glWgpuDiff = ScreenshotHelper.diffPercentage(gl, wgpu, wgpuTolerance.maxChannelDiff());
            assertTrue(glWgpuDiff < wgpuTolerance.maxDiffPercent(),
                    "Cross-backend '" + name + "': GL/WebGPU differ by "
                            + String.format("%.1f%%", glWgpuDiff)
                            + " (max " + wgpuTolerance.maxDiffPercent() + "%). "
                            + "Screenshots in build/screenshots/opengl/" + name + ".png and build/screenshots/webgpu/" + name + ".png");
        }
    }

    /** Renders on OpenGL and asserts it matches the named reference screenshot. */
    public void assertOpenGlMatchesReference(RenderTestScene scene, String referenceName) throws IOException {
        byte[] gl = renderOpenGl(scene);
        saveScreenshot(gl, "opengl", referenceName);
        assertMatchesReference(gl, referenceName, "opengl", "OpenGL");
    }

    /** Renders on Vulkan and asserts it matches the named reference screenshot. */
    public void assertVulkanMatchesReference(RenderTestScene scene, String referenceName) throws IOException {
        byte[] vk = renderVulkan(scene);
        saveScreenshot(vk, "vulkan", referenceName);
        assertMatchesReference(vk, referenceName, "vulkan", "Vulkan");
    }

    /** Renders on WebGPU and asserts it matches the named reference screenshot. Skips if wgpu-native unavailable. */
    public void assertWebGpuMatchesReference(RenderTestScene scene, String referenceName) throws IOException {
        byte[] wgpu = renderWebGpu(scene);
        if (wgpu == null) {
            System.out.println("WebGPU not available — skipping");
            return;
        }
        saveScreenshot(wgpu, "webgpu", referenceName);
        assertMatchesReference(wgpu, referenceName, "webgpu", "WebGPU");
    }

    /** Renders on both backends and asserts both match each other AND the reference. */
    public void assertAll(RenderTestScene scene, String referenceName) throws IOException {
        byte[] gl = renderOpenGl(scene);
        byte[] vk = renderVulkan(scene);
        saveScreenshot(gl, "opengl", referenceName);
        saveScreenshot(vk, "vulkan", referenceName);

        // Cross-backend
        double crossDiff = ScreenshotHelper.diffPercentage(gl, vk, channelTolerance);
        assertTrue(crossDiff < maxDiffPercent,
                "Backends differ by " + String.format("%.1f%%", crossDiff) + "%");

        // vs reference (per-backend subdirectory with fallback)
        byte[] glRef = loadReference("opengl", referenceName);
        if (glRef != null) {
            assertMatchesReference(gl, glRef, "OpenGL", referenceName);
        }
        byte[] vkRef = loadReference("vulkan", referenceName);
        if (vkRef != null) {
            assertMatchesReference(vk, vkRef, "Vulkan", referenceName);
        }
    }

    /** Saves the current render as a new reference screenshot. */
    public void saveReference(RenderTestScene scene, String referenceName) throws IOException {
        byte[] gl = renderOpenGl(scene);
        var path = "examples/src/test/resources/reference-screenshots/" + referenceName + ".png";
        ScreenshotHelper.save(gl, width, height, path);
        System.out.println("Reference saved: " + path);
    }

    // --- Rendering ---

    public byte[] renderOpenGl(RenderTestScene scene) {
        var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        try {
            var window = toolkit.createWindow(new WindowDescriptor("GL Test", width, height));
            var device = new GlRenderDevice(window);
            return renderWith(device, scene);
        } finally {
            toolkit.close();
        }
    }

    public byte[] renderVulkan(RenderTestScene scene) {
        var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
        try {
            var window = toolkit.createWindow(new WindowDescriptor("Vk Test", width, height));
            var device = new VkRenderDevice(
                    GLFWVulkan.glfwGetRequiredInstanceExtensions(),
                    instance -> GlfwWindowToolkit.createVulkanSurface(instance, window.nativeHandle()),
                    width, height);
            return renderWith(device, scene);
        } finally {
            toolkit.close();
        }
    }

    /** Renders the scene on WebGPU with a GLFW window + surface. Returns null if wgpu-native is not available. */
    public byte[] renderWebGpu(RenderTestScene scene) {
        if (!WgpuRenderDevice.isAvailable()) {
            return null;
        }
        var toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.NO_API_HINTS);
        try {
            var window = toolkit.createWindow(new WindowDescriptor("WGPU Test", width, height));
            var device = new WgpuRenderDevice(window);
            return renderWith(device, scene);
        } finally {
            toolkit.close();
        }
    }

    private byte[] renderWith(RenderDevice device, RenderTestScene scene) {
        var renderer = new Renderer(device);
        renderer.setViewport(width, height);
        scene.setup(renderer, width, height);

        for (int i = 0; i < 3; i++) {
            renderer.renderFrame();
        }

        byte[] pixels = device.readFramebuffer(width, height);
        renderer.close(); // closes renderer AND device
        return pixels;
    }

    // --- Screenshot saving ---

    /**
     * Saves a screenshot to {@code build/screenshots/<backend>/<name>.png} for human review.
     */
    public void saveScreenshot(byte[] pixels, String backend, String name) {
        try {
            var dir = new File("build/screenshots/" + backend);
            dir.mkdirs();
            ScreenshotHelper.save(pixels, width, height, dir.getPath() + "/" + name + ".png");
        } catch (IOException e) {
            // non-fatal — screenshot saving should not fail tests
            System.err.println("Warning: failed to save screenshot " + backend + "/" + name + ": " + e.getMessage());
        }
    }

    // --- Reference comparison ---

    private void assertMatchesReference(byte[] actual, String referenceName, String backendDir, String backendLabel) throws IOException {
        byte[] ref = loadReference(backendDir, referenceName);
        if (ref == null) {
            System.out.println("No reference screenshot for '" + referenceName + "' (" + backendLabel + ") — skipping reference check");
            return;
        }
        assertMatchesReference(actual, ref, backendLabel, referenceName);
    }

    private void assertMatchesReference(byte[] actual, byte[] ref, String backendName, String referenceName) {
        double diffPct = ScreenshotHelper.diffPercentage(actual, ref, channelTolerance);
        assertTrue(diffPct < maxDiffPercent,
                backendName + " differs from reference '" + referenceName + "' by "
                        + String.format("%.1f%%", diffPct) + "% (max " + maxDiffPercent + "%)");
    }

    /**
     * Loads a reference screenshot, first checking the per-backend subdirectory
     * ({@code reference-screenshots/<backendDir>/<name>.png}), then falling back
     * to the flat directory ({@code reference-screenshots/<name>.png}).
     */
    private byte[] loadReference(String backendDir, String name) throws IOException {
        // Try per-backend subdirectory first
        byte[] result = loadReferenceFromPath("/reference-screenshots/" + backendDir + "/" + name + ".png");
        if (result != null) return result;
        // Fall back to flat directory
        return loadReferenceFromPath("/reference-screenshots/" + name + ".png");
    }

    private byte[] loadReferenceFromPath(String resourcePath) throws IOException {
        InputStream stream = getClass().getResourceAsStream(resourcePath);
        if (stream == null) return null;
        try (stream) {
            BufferedImage img = ImageIO.read(stream);
            if (img == null || img.getWidth() != width || img.getHeight() != height) return null;

            byte[] rgba = new byte[width * height * 4];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = img.getRGB(x, y);
                    int i = (y * width + x) * 4;
                    rgba[i]     = (byte) ((argb >> 16) & 0xFF);
                    rgba[i + 1] = (byte) ((argb >> 8) & 0xFF);
                    rgba[i + 2] = (byte) (argb & 0xFF);
                    rgba[i + 3] = (byte) ((argb >> 24) & 0xFF);
                }
            }
            return rgba;
        }
    }
}
