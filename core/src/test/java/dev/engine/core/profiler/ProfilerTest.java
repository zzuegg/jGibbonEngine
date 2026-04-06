package dev.engine.core.profiler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfilerTest {

    private Profiler profiler;

    @BeforeEach
    void setUp() { profiler = new Profiler(); }

    @Nested
    class BasicScopes {
        @Test void singleScopeRecordsDuration() {
            try (var scope = profiler.scope("render")) {
                spinWait(1_000_000); // ~1ms
            }
            var results = profiler.currentFrame();
            assertNotNull(results);
            var render = results.get("render");
            assertNotNull(render);
            assertTrue(render.cpuNanos() > 0);
        }

        @Test void nestedScopes() {
            try (var outer = profiler.scope("frame")) {
                try (var inner = profiler.scope("shadows")) {
                    spinWait(500_000);
                }
                try (var inner = profiler.scope("lighting")) {
                    spinWait(500_000);
                }
            }
            var results = profiler.currentFrame();
            var frame = results.get("frame");
            assertNotNull(frame);
            assertEquals(2, frame.children().size());
            assertNotNull(frame.children().get("shadows"));
            assertNotNull(frame.children().get("lighting"));
        }

        @Test void childDurationsAreShorterThanParent() {
            try (var outer = profiler.scope("parent")) {
                try (var inner = profiler.scope("child")) {
                    spinWait(500_000);
                }
            }
            var results = profiler.currentFrame();
            var parent = results.get("parent");
            var child = parent.children().get("child");
            assertTrue(parent.cpuNanos() >= child.cpuNanos());
        }
    }

    @Nested
    class FrameReset {
        @Test void newFrameClearsResults() {
            try (var s = profiler.scope("old")) { spinWait(100_000); }
            profiler.newFrame();
            try (var s = profiler.scope("new")) { spinWait(100_000); }
            var results = profiler.currentFrame();
            assertNull(results.get("old"));
            assertNotNull(results.get("new"));
        }
    }

    @Nested
    class RenderStatsTests {
        @Test void trackDrawCalls() {
            var stats = new RenderStats();
            stats.recordDrawCall(100, 0);
            stats.recordDrawCall(200, 6);
            assertEquals(2, stats.drawCalls());
            assertEquals(300, stats.verticesSubmitted());
            assertEquals(6, stats.indicesSubmitted());
        }

        @Test void trackStateChanges() {
            var stats = new RenderStats();
            stats.recordPipelineBind();
            stats.recordPipelineBind();
            stats.recordTextureBind();
            assertEquals(2, stats.pipelineBinds());
            assertEquals(1, stats.textureBinds());
        }

        @Test void resetClearsAll() {
            var stats = new RenderStats();
            stats.recordDrawCall(100, 0);
            stats.recordPipelineBind();
            stats.reset();
            assertEquals(0, stats.drawCalls());
            assertEquals(0, stats.pipelineBinds());
        }
    }

    private static void spinWait(long nanos) {
        long start = System.nanoTime();
        while (System.nanoTime() - start < nanos) {
            Thread.onSpinWait();
        }
    }
}
