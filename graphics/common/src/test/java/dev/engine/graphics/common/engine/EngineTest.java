package dev.engine.graphics.common.engine;

import dev.engine.core.module.AbstractModule;
import dev.engine.core.module.ModuleManager;
import dev.engine.core.module.Time;
import dev.engine.graphics.common.HeadlessRenderDevice;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EngineTest {

    @Nested
    class EngineLifecycle {
        @Test void createAndShutdown() {
            var config = EngineConfig.builder()
                    .headless(true)
                    .threaded(false)
                    .build();
            var engine = new Engine(config);
            assertNotNull(engine.modules());
            assertNotNull(engine.assets());
            assertNotNull(engine.renderer());
            engine.shutdown();
        }

        @Test void singleThreadedTick() {
            var config = EngineConfig.builder().headless(true).threaded(false).build();
            var engine = new Engine(config);

            var tickCount = new AtomicInteger(0);
            engine.modules().add(new AbstractModule<Time>() {
                @Override protected void doUpdate(Time time) {
                    tickCount.incrementAndGet();
                }
            });

            engine.tick(0.016);
            engine.tick(0.016);
            assertEquals(2, tickCount.get());
            engine.shutdown();
        }
    }

    @Nested
    class ModuleAccess {
        @Test void assetModuleAccessible() {
            var config = EngineConfig.builder().headless(true).threaded(false).build();
            var engine = new Engine(config);
            assertNotNull(engine.assets());
            engine.shutdown();
        }

        @Test void rendererAccessible() {
            var config = EngineConfig.builder().headless(true).threaded(false).build();
            var engine = new Engine(config);
            assertNotNull(engine.renderer());
            engine.shutdown();
        }

        @Test void sceneAccessible() {
            var config = EngineConfig.builder().headless(true).threaded(false).build();
            var engine = new Engine(config);
            assertNotNull(engine.scene());
            engine.shutdown();
        }
    }

    @Nested
    class UserModules {
        @Test void userModuleGetsInitAndUpdate() {
            var config = EngineConfig.builder().headless(true).threaded(false).build();
            var engine = new Engine(config);

            var events = new ArrayList<String>();
            engine.modules().add(new AbstractModule<Time>() {
                @Override protected void doInit(ModuleManager<Time> manager) {
                    events.add("init");
                }
                @Override protected void doUpdate(Time time) {
                    events.add("update");
                }
                @Override protected void doDeinit() {
                    events.add("deinit");
                }
            });

            engine.tick(0.016);
            engine.shutdown();

            assertEquals("init", events.get(0));
            assertEquals("update", events.get(1));
            assertEquals("deinit", events.get(2));
        }
    }
}
