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
    void frameCountersResetIndependentlyOfLiveTotals() {
        var stats = new ResourceStats();
        stats.recordCreate("texture");
        stats.recordCreate("texture");
        stats.recordDestroy("texture");

        assertEquals(1, stats.liveCount("texture"));
        assertEquals(2, stats.frameCreated("texture"));
        assertEquals(1, stats.frameDestroyed("texture"));

        stats.resetFrameCounters();
        // Live total persists
        assertEquals(1, stats.liveCount("texture"));
        // Frame counters reset
        assertEquals(0, stats.frameCreated("texture"));
        assertEquals(0, stats.frameDestroyed("texture"));
    }

    @Test
    void unknownTypeReturnsZero() {
        var stats = new ResourceStats();
        assertEquals(0, stats.liveCount("nonexistent"));
        assertEquals(0, stats.frameCreated("nonexistent"));
        assertEquals(0, stats.frameDestroyed("nonexistent"));
    }

    @Test
    void autoRegistersOnFirstUse() {
        var stats = new ResourceStats();
        assertFalse(stats.resourceTypes().contains("sampler"));
        stats.recordCreate("sampler");
        assertTrue(stats.resourceTypes().contains("sampler"));
        assertEquals(1, stats.liveCount("sampler"));
    }

    @Test
    void totalAggregatesAcrossTypes() {
        var stats = new ResourceStats();
        stats.recordCreate("buffer");
        stats.recordCreate("buffer");
        stats.recordCreate("texture");
        stats.recordCreate("pipeline");
        stats.recordDestroy("buffer");

        assertEquals(3, stats.totalLiveCount());
        assertEquals(4, stats.totalFrameCreated());
        assertEquals(1, stats.totalFrameDestroyed());
    }

    @Test
    void preRegisterMakesTypeDiscoverable() {
        var stats = new ResourceStats();
        stats.register("vertex_input");
        assertTrue(stats.resourceTypes().contains("vertex_input"));
        assertEquals(0, stats.liveCount("vertex_input"));
    }

    @Test
    void toStringIncludesAllTypes() {
        var stats = new ResourceStats();
        stats.recordCreate("buffer");
        stats.recordCreate("buffer");
        stats.recordDestroy("buffer");
        var str = stats.toString();
        assertTrue(str.contains("buffer"));
        assertTrue(str.contains("1 live"));
    }
}
