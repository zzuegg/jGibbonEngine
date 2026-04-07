package dev.engine.tests.screenshot.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ImageUtilsTest {

    @Test
    void identicalImagesHaveZeroDiff() {
        byte[] a = {(byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 255};
        byte[] b = {(byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 255};
        assertEquals(0.0, ImageUtils.diffPercentage(a, b, 0));
    }

    @Test
    void completelyDifferentImagesReturn100() {
        byte[] a = {(byte) 255, 0, 0, (byte) 255, 0, 0, 0, (byte) 255};
        byte[] b = {0, (byte) 255, 0, (byte) 255, (byte) 255, 0, 0, (byte) 255};
        assertEquals(100.0, ImageUtils.diffPercentage(a, b, 0));
    }

    @Test
    void withinThresholdCountsAsMatch() {
        byte[] a = {100, 100, 100, (byte) 255};
        byte[] b = {102, 101, 100, (byte) 255};
        assertEquals(0.0, ImageUtils.diffPercentage(a, b, 2));
    }

    @Test
    void saveAndLoadRoundTrips(@TempDir Path tmp) throws Exception {
        byte[] original = {(byte) 255, 0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 255};
        var file = tmp.resolve("test.png");
        ImageUtils.savePng(original, 2, 1, file);
        byte[] loaded = ImageUtils.loadPng(file, 2, 1);
        assertArrayEquals(original, loaded);
    }
}
