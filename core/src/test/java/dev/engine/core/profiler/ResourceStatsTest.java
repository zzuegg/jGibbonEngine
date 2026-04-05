package dev.engine.core.profiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResourceStatsTest {

    @Test
    void createAndDestroyTracksLiveTotal() {
        var stats = new ResourceStats();
        stats.recordCreate("buffer");
        stats.recordCreate("buffer");
        stats.recordCreate("buffer");
        assertEquals(3, stats.liveCount("buffer"));

        stats.recordDestroy("buffer");
        assertEquals(2, stats.liveCount("buffer"));
    }

    @Test
    void newFrameSwapsCountersAndResets() {
        var stats = new ResourceStats();
        stats.recordCreate("texture");
        stats.recordCreate("texture");
        stats.recordDestroy("texture");
        stats.recordUse("texture");
        stats.recordUse("texture");
        stats.recordUse("texture");
        stats.recordUpdate("texture");

        // Before swap: current frame has the data
        assertEquals(2, stats.currentFrameCreated("texture"));
        assertEquals(1, stats.currentFrameDestroyed("texture"));
        assertEquals(3, stats.currentFrameUsed("texture"));
        assertEquals(1, stats.currentFrameUpdated("texture"));

        // Last frame should be zero (no previous swap)
        assertEquals(0, stats.lastFrameCreated("texture"));

        stats.newFrame();

        // After swap: last frame has the data
        assertEquals(2, stats.lastFrameCreated("texture"));
        assertEquals(1, stats.lastFrameDestroyed("texture"));
        assertEquals(3, stats.lastFrameUsed("texture"));
        assertEquals(1, stats.lastFrameUpdated("texture"));

        // Current frame reset to zero
        assertEquals(0, stats.currentFrameCreated("texture"));
        assertEquals(0, stats.currentFrameDestroyed("texture"));
        assertEquals(0, stats.currentFrameUsed("texture"));
        assertEquals(0, stats.currentFrameUpdated("texture"));

        // Live total persists across frame swap
        assertEquals(1, stats.liveCount("texture"));
    }

    @Test
    void consecutiveFrameSwapsOverwriteLastFrame() {
        var stats = new ResourceStats();
        stats.recordCreate("buffer");
        stats.recordCreate("buffer");
        stats.newFrame();

        assertEquals(2, stats.lastFrameCreated("buffer"));

        // New frame with different data
        stats.recordCreate("buffer");
        stats.newFrame();

        // Last frame now shows frame 2 data, not frame 1
        assertEquals(1, stats.lastFrameCreated("buffer"));
        assertEquals(3, stats.liveCount("buffer"));
    }

    @Test
    void unknownTypeReturnsZero() {
        var stats = new ResourceStats();
        assertEquals(0, stats.liveCount("nonexistent"));
        assertEquals(0, stats.lastFrameCreated("nonexistent"));
        assertEquals(0, stats.lastFrameUsed("nonexistent"));
        assertEquals(0, stats.currentFrameUpdated("nonexistent"));
    }

    @Test
    void autoRegistersOnFirstUse() {
        var stats = new ResourceStats();
        assertFalse(stats.resourceTypes().contains("sampler"));
        stats.recordCreate("sampler");
        assertTrue(stats.resourceTypes().contains("sampler"));
    }

    @Test
    void useAndUpdateAreIndependent() {
        var stats = new ResourceStats();
        stats.recordUse("buffer");
        stats.recordUse("buffer");
        stats.recordUpdate("buffer");
        stats.newFrame();

        assertEquals(2, stats.lastFrameUsed("buffer"));
        assertEquals(1, stats.lastFrameUpdated("buffer"));
        assertEquals(0, stats.lastFrameCreated("buffer"));
        assertEquals(0, stats.liveCount("buffer"));
    }

    @Test
    void totalAggregatesAcrossTypes() {
        var stats = new ResourceStats();
        stats.recordCreate("buffer");
        stats.recordCreate("buffer");
        stats.recordCreate("texture");
        stats.recordCreate("pipeline");
        stats.recordDestroy("buffer");
        stats.recordUse("buffer");
        stats.recordUse("texture");
        stats.recordUpdate("buffer");

        assertEquals(3, stats.totalLiveCount());

        stats.newFrame();

        assertEquals(4, stats.totalLastFrameCreated());
        assertEquals(1, stats.totalLastFrameDestroyed());
        assertEquals(2, stats.totalLastFrameUsed());
        assertEquals(1, stats.totalLastFrameUpdated());
    }

    @Test
    void preRegisterMakesTypeDiscoverable() {
        var stats = new ResourceStats();
        stats.register("vertex_input");
        assertTrue(stats.resourceTypes().contains("vertex_input"));
        assertEquals(0, stats.liveCount("vertex_input"));
    }

    @Test
    void toStringIncludesLastFrameData() {
        var stats = new ResourceStats();
        stats.recordCreate("buffer");
        stats.recordCreate("buffer");
        stats.recordDestroy("buffer");
        stats.recordUse("buffer");
        stats.recordUpdate("buffer");
        stats.newFrame();

        var str = stats.toString();
        assertTrue(str.contains("buffer"));
        assertTrue(str.contains("1 live"));
    }
}
