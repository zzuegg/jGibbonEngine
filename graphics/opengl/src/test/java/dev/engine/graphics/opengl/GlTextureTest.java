package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.texture.TextureDescriptor;
import dev.engine.graphics.texture.TextureFormat;
import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL45;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

class GlTextureTest {

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

    @Nested
    class TextureCreation {
        @Test
        void createTextureReturnsValidHandle() {
            var desc = new TextureDescriptor(64, 64, TextureFormat.RGBA8);
            var handle = device.createTexture(desc);
            assertNotEquals(Handle.invalid(), handle);
            assertTrue(device.isValidTexture(handle));
        }

        @Test
        void destroyTextureInvalidatesHandle() {
            var desc = new TextureDescriptor(32, 32, TextureFormat.RGBA8);
            var handle = device.createTexture(desc);
            device.destroyTexture(handle);
            assertFalse(device.isValidTexture(handle));
        }
    }

    @Nested
    class TextureUpload {
        @Test
        void uploadPixelData() {
            var desc = new TextureDescriptor(2, 2, TextureFormat.RGBA8);
            var handle = device.createTexture(desc);

            // 2x2 RGBA = 16 bytes
            ByteBuffer pixels = ByteBuffer.allocateDirect(16);
            // Red pixel
            pixels.put((byte) 255).put((byte) 0).put((byte) 0).put((byte) 255);
            // Green pixel
            pixels.put((byte) 0).put((byte) 255).put((byte) 0).put((byte) 255);
            // Blue pixel
            pixels.put((byte) 0).put((byte) 0).put((byte) 255).put((byte) 255);
            // White pixel
            pixels.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) 255);
            pixels.flip();

            assertDoesNotThrow(() -> device.uploadTexture(handle, pixels));

            // Verify GPU has the texture by reading it back
            int glName = device.getGlTextureName(handle);
            ByteBuffer readback = ByteBuffer.allocateDirect(16);
            GL45.glGetTextureImage(glName, 0, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, readback);

            // First pixel should be red
            assertEquals((byte) 255, readback.get(0)); // R
            assertEquals((byte) 0, readback.get(1));    // G
            assertEquals((byte) 0, readback.get(2));    // B
            assertEquals((byte) 255, readback.get(3));  // A

            device.destroyTexture(handle);
        }
    }
}
