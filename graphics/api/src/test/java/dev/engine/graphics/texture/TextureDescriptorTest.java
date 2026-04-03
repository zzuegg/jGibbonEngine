package dev.engine.graphics.texture;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test void texture3dDescriptor() {
        var desc = TextureDescriptor.texture3d(64, 64, 32, TextureFormat.RGBA8);
        assertEquals(TextureType.TEXTURE_3D, desc.type());
        assertEquals(32, desc.depth());
        assertEquals(1, desc.layers());
    }

    @Test void texture2dArrayDescriptor() {
        var desc = TextureDescriptor.texture2dArray(256, 256, 4, TextureFormat.RGBA8);
        assertEquals(TextureType.TEXTURE_2D_ARRAY, desc.type());
        assertEquals(4, desc.layers());
        assertEquals(1, desc.depth());
    }

    @Test void cubeMapDescriptor() {
        var desc = TextureDescriptor.cubeMap(512, TextureFormat.RGBA8);
        assertEquals(TextureType.TEXTURE_CUBE, desc.type());
        assertEquals(6, desc.layers());
        assertEquals(512, desc.width());
        assertEquals(512, desc.height());
    }

    @Test void backwardCompatConstructorsStillWork() {
        var desc2 = new TextureDescriptor(256, 256, TextureFormat.RGBA8);
        assertEquals(TextureType.TEXTURE_2D, desc2.type());
        assertEquals(1, desc2.depth());
        assertEquals(1, desc2.layers());

        var desc4 = new TextureDescriptor(256, 256, TextureFormat.RGBA8, MipMode.NONE);
        assertEquals(TextureType.TEXTURE_2D, desc4.type());
        assertEquals(1, desc4.depth());
        assertEquals(1, desc4.layers());
    }

    @Test void autoMipLevelCount() {
        assertEquals(-1, MipMode.AUTO.levelCount());
    }

    @Test void noneMipLevelCount() {
        assertEquals(1, MipMode.NONE.levelCount());
    }
}
