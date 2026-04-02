package dev.engine.core.asset;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

public class ImageLoader implements AssetLoader<ImageData> {

    private static final Set<String> EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".bmp", ".gif");

    @Override
    public boolean supports(String path) {
        var lower = path.toLowerCase();
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    @Override
    public ImageData load(AssetSource.AssetData data) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(data.bytes()));
            if (img == null) throw new RuntimeException("Failed to decode image: " + data.path());

            int w = img.getWidth();
            int h = img.getHeight();
            int channels = 4; // always output RGBA
            ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * channels);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = img.getRGB(x, y);
                    pixels.put((byte) ((argb >> 16) & 0xFF)); // R
                    pixels.put((byte) ((argb >> 8) & 0xFF));  // G
                    pixels.put((byte) (argb & 0xFF));         // B
                    pixels.put((byte) ((argb >> 24) & 0xFF)); // A
                }
            }
            pixels.flip();
            return new ImageData(w, h, channels, pixels);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load image: " + data.path(), e);
        }
    }

    @Override
    public Class<ImageData> assetType() {
        return ImageData.class;
    }
}
