package dev.engine.graphics.opengl;

import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlGpuTimerTest {

    static GlfwWindowToolkit toolkit;
    static GlRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("GPU Test", 1, 1));
        device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Test
    void gpuTimerReturnsNonNegativeDuration() {
        var timer = new GlGpuTimer();
        timer.begin();
        // Issue some GL work
        var ctx = device.beginFrame();
        ctx.clear(0f, 0f, 0f, 1f);
        device.endFrame(ctx);
        timer.end();

        // GPU timer results are async — wait for result
        long nanos = timer.waitForResult();
        assertTrue(nanos >= 0, "GPU time should be non-negative, got " + nanos);
    }
}
