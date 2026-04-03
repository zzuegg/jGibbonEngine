package dev.engine.graphics.texture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextureDescriptorTest {
    @Test void rgbaDefaultsToAutoMips() {
        var desc = TextureDescriptor.rgba(512, 512);
        assertEquals(MipMode.AUTO, desc.mipMode());
        assertEquals(TextureFormat.RGBA8, desc.format());
    }

    @Test void depthDefaultsToNoMips() {
        var desc = TextureDescriptor.depth(1024, 1024);
        assertEquals(MipMode.NONE, desc.mipMode());
    }

    @Test void backwardCompatConstructor() {
        var desc = new TextureDescriptor(256, 256, TextureFormat.RGBA8);
        assertEquals(MipMode.AUTO, desc.mipMode());
    }

    @Test void backwardCompatConstructorDepth() {
        var desc = new TextureDescriptor(256, 256, TextureFormat.DEPTH32F);
        assertEquals(MipMode.NONE, desc.mipMode());
    }

    @Test void explicitMipLevels() {
        var mode = MipMode.levels(5);
        assertEquals(5, mode.levelCount());
    }

    @Test void withMipMode() {
        var desc = TextureDescriptor.rgba(512, 512).withMipMode(MipMode.NONE);
        assertEquals(MipMode.NONE, desc.mipMode());
    }

    @Test void hdrFormatsExist() {
        assertNotNull(TextureFormat.RGBA16F);
        assertNotNull(TextureFormat.RGBA32F);
        assertNotNull(TextureFormat.RG16F);
        assertNotNull(TextureFormat.R32F);
        assertNotNull(TextureFormat.R32UI);
        assertNotNull(TextureFormat.R32I);
    }

    @Test void autoMipLevelCount() {
        assertEquals(-1, MipMode.AUTO.levelCount());
    }

    @Test void noneMipLevelCount() {
        assertEquals(1, MipMode.NONE.levelCount());
    }
}
