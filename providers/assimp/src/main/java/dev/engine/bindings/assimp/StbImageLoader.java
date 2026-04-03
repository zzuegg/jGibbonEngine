package dev.engine.bindings.assimp;

import dev.engine.core.asset.AssetLoader;
import dev.engine.core.asset.AssetSource;
import dev.engine.core.asset.TextureData;

import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Set;

/**
 * Loads images via STB Image (through LWJGL bindings).
 * Returns {@link TextureData} with RGBA8 pixel data.
 */
public class StbImageLoader implements AssetLoader<TextureData> {

    private static final Set<String> EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".bmp", ".tga", ".hdr", ".gif"
    );

    @Override
    public boolean supports(String path) {
        var lower = path.toLowerCase();
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    @Override
    public Class<TextureData> assetType() {
        return TextureData.class;
    }

    @Override
    public TextureData load(AssetSource.AssetData data) {
        // Use LWJGL's allocator for the input buffer to stay in the same allocator domain
        ByteBuffer inputBuffer = MemoryUtil.memAlloc(data.bytes().length);
        try {
            inputBuffer.put(data.bytes());
            inputBuffer.flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer widthBuf = stack.mallocInt(1);
                IntBuffer heightBuf = stack.mallocInt(1);
                IntBuffer channelsBuf = stack.mallocInt(1);

                // Force 4 channels (RGBA)
                ByteBuffer stbPixels = STBImage.stbi_load_from_memory(
                        inputBuffer, widthBuf, heightBuf, channelsBuf, 4
                );

                if (stbPixels == null) {
                    throw new RuntimeException("STB failed to load image: " + data.path()
                            + " — " + STBImage.stbi_failure_reason());
                }

                try {
                    int width = widthBuf.get(0);
                    int height = heightBuf.get(0);

                    // Copy pixel data to a Java-managed direct buffer before freeing STB's buffer
                    int size = stbPixels.remaining();
                    ByteBuffer pixels = ByteBuffer.allocateDirect(size)
                            .order(ByteOrder.nativeOrder());
                    pixels.put(stbPixels);
                    pixels.flip();

                    return TextureData.rgba(width, height, pixels);
                } finally {
                    // Reset position before freeing — LWJGL's stbi_image_free uses
                    // memAddress() which adds the buffer's position to the base address.
                    stbPixels.position(0);
                    STBImage.stbi_image_free(stbPixels);
                }
            }
        } finally {
            MemoryUtil.memFree(inputBuffer);
        }
    }
}
