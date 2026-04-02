package dev.engine.core.asset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class ImageLoaderTest {

    @TempDir Path tempDir;

    @Test
    void loadPngImage() throws IOException {
        // Create a 4x4 red PNG
        var img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 4; y++)
            for (int x = 0; x < 4; x++)
                img.setRGB(x, y, 0xFFFF0000); // ARGB red
        ImageIO.write(img, "png", tempDir.resolve("red.png").toFile());

        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new ImageLoader());

        var imageData = manager.loadSync("red.png", ImageData.class);
        assertEquals(4, imageData.width());
        assertEquals(4, imageData.height());
        assertEquals(4, imageData.channels());
        assertEquals(4 * 4 * 4, imageData.pixels().remaining()); // 4x4 RGBA

        // First pixel should be red (RGBA)
        assertEquals((byte) 0xFF, imageData.pixels().get(0)); // R
        assertEquals((byte) 0x00, imageData.pixels().get(1)); // G
        assertEquals((byte) 0x00, imageData.pixels().get(2)); // B
        assertEquals((byte) 0xFF, imageData.pixels().get(3)); // A
    }

    @Test
    void loadJpgImage() throws IOException {
        var img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++)
            for (int x = 0; x < 2; x++)
                img.setRGB(x, y, 0x00FF00); // green
        ImageIO.write(img, "jpg", tempDir.resolve("green.jpg").toFile());

        var manager = new AssetManager(Executors.newSingleThreadExecutor());
        manager.addSource(new FileSystemAssetSource(tempDir));
        manager.registerLoader(new ImageLoader());

        var imageData = manager.loadSync("green.jpg", ImageData.class);
        assertEquals(2, imageData.width());
        assertEquals(2, imageData.height());
        assertNotNull(imageData.pixels());
    }

    @Test
    void supportsCommonExtensions() {
        var loader = new ImageLoader();
        assertTrue(loader.supports("texture.png"));
        assertTrue(loader.supports("texture.jpg"));
        assertTrue(loader.supports("texture.jpeg"));
        assertTrue(loader.supports("texture.bmp"));
        assertFalse(loader.supports("model.obj"));
        assertFalse(loader.supports("data.txt"));
    }
}
