package dev.engine.bindings.assimp;

import dev.engine.core.asset.AssetManager;
import dev.engine.core.asset.AssetSource;
import dev.engine.core.asset.TextureData;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class StbImageLoaderTest {

    @Test
    void loadSmallPng() throws IOException {
        // Create a small 2x2 PNG in memory: red, green, blue, white
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFF0000); // red
        img.setRGB(1, 0, 0xFF00FF00); // green
        img.setRGB(0, 1, 0xFF0000FF); // blue
        img.setRGB(1, 1, 0xFFFFFFFF); // white

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        byte[] bytes = baos.toByteArray();

        AssetManager manager = new AssetManager(Runnable::run);
        manager.addSource(new AssetSource() {
            @Override
            public AssetData load(String path) {
                return new AssetData(path, bytes);
            }

            @Override
            public boolean exists(String path) {
                return path.equals("test.png");
            }
        });
        manager.registerLoader(new StbImageLoader());

        TextureData texture = manager.loadSync("test.png", TextureData.class);

        assertEquals(2, texture.width());
        assertEquals(2, texture.height());
        assertEquals(4, texture.channels());
        assertFalse(texture.compressed());

        // Verify pixel values (RGBA order, row by row)
        var pixels = texture.pixels();

        // Red pixel (0,0)
        assertEquals((byte) 0xFF, pixels.get(0)); // R
        assertEquals((byte) 0x00, pixels.get(1)); // G
        assertEquals((byte) 0x00, pixels.get(2)); // B
        assertEquals((byte) 0xFF, pixels.get(3)); // A

        // Green pixel (1,0)
        assertEquals((byte) 0x00, pixels.get(4)); // R
        assertEquals((byte) 0xFF, pixels.get(5)); // G
        assertEquals((byte) 0x00, pixels.get(6)); // B
        assertEquals((byte) 0xFF, pixels.get(7)); // A

        // Blue pixel (0,1)
        assertEquals((byte) 0x00, pixels.get(8));  // R
        assertEquals((byte) 0x00, pixels.get(9));  // G
        assertEquals((byte) 0xFF, pixels.get(10)); // B
        assertEquals((byte) 0xFF, pixels.get(11)); // A

        // White pixel (1,1)
        assertEquals((byte) 0xFF, pixels.get(12)); // R
        assertEquals((byte) 0xFF, pixels.get(13)); // G
        assertEquals((byte) 0xFF, pixels.get(14)); // B
        assertEquals((byte) 0xFF, pixels.get(15)); // A
    }

    @Test
    void supportsCorrectExtensions() {
        var loader = new StbImageLoader();
        assertTrue(loader.supports("texture.png"));
        assertTrue(loader.supports("photo.JPG"));
        assertTrue(loader.supports("image.jpeg"));
        assertTrue(loader.supports("sprite.bmp"));
        assertTrue(loader.supports("icon.tga"));
        assertTrue(loader.supports("env.hdr"));
        assertTrue(loader.supports("anim.gif"));
        assertFalse(loader.supports("model.fbx"));
        assertFalse(loader.supports("data.dds"));
    }
}
