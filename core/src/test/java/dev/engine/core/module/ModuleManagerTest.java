package dev.engine.core.module;

import dev.engine.core.module.exception.CyclicDependencyException;
import dev.engine.core.module.exception.DependentModuleException;
import dev.engine.core.module.exception.MissingDependencyException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleManagerTest {

    private static class BaseModule extends AbstractModule<Time> {
        final List<String> events = Collections.synchronizedList(new ArrayList<>());
        final List<Time> updateContexts = Collections.synchronizedList(new ArrayList<>());
        Consumer<String> lifecycleCallback;

        @Override
        protected void doCreate() {
            events.add("create");
        }

        @Override
        protected void doInit(ModuleManager<Time> manager) {
            events.add("init");
        }

        @Override
        protected void doUpdate(Time context) {
            events.add("update");
            updateContexts.add(context);
        }

        @Override
        protected void doDeinit() {
            events.add("deinit");
            if (lifecycleCallback != null) {
                lifecycleCallback.accept("deinit");
            }
        }

        @Override
        protected void doCleanup() {
            events.add("cleanup");
            if (lifecycleCallback != null) {
                lifecycleCallback.accept("cleanup");
            }
        }
    }

    private static class ModuleA extends BaseModule {
    }

    private static class ModuleB extends BaseModule {
        @Override
        public Set<Class<? extends Module<Time>>> getDependencies() {
            return Set.of(ModuleA.class);
        }
    }

    private static class ModuleC extends BaseModule {
        @Override
        public Set<Class<? extends Module<Time>>> getDependencies() {
            return Set.of(ModuleA.class);
        }
    }

    private static class ModuleD extends BaseModule {
        @Override
        public Set<Class<? extends Module<Time>>> getDependencies() {
            return Set.of(ModuleB.class, ModuleC.class);
        }
    }

    private static class IndependentModule extends BaseModule {
    }

    private static class FailingUpdateModule extends BaseModule {
        @Override
        protected void doUpdate(Time context) {
            super.doUpdate(context);
            throw new RuntimeException("deliberate test failure");
        }
    }

    private static class CyclicModuleX extends BaseModule {
        @Override
        public Set<Class<? extends Module<Time>>> getDependencies() {
            return Set.of(CyclicModuleY.class);
        }
    }

    private static class CyclicModuleY extends BaseModule {
        @Override
        public Set<Class<? extends Module<Time>>> getDependencies() {
            return Set.of(CyclicModuleX.class);
        }
    }

    private static class LookupModule extends AbstractModule<Time> {
        Module<Time> foundDep = null;

        @Override
        protected void doUpdate(Time ctx) {
        }

        @Override
        protected void doInit(ModuleManager<Time> m) {
            foundDep = m.getModule(ModuleA.class);
        }

        @Override
        public Set<Class<? extends Module<Time>>> getDependencies() {
            return Set.of(ModuleA.class);
        }
    }

    private static class ThreadModA extends AbstractModule<Time> {
        volatile String updateThreadName;
        final CountDownLatch arrivedLatch;
        final CountDownLatch proceedLatch;

        ThreadModA(CountDownLatch arrivedLatch, CountDownLatch proceedLatch) {
            this.arrivedLatch = arrivedLatch;
            this.proceedLatch = proceedLatch;
        }

        @Override
        protected void doUpdate(Time ctx) {
            updateThreadName = Thread.currentThread().getName();
            arrivedLatch.countDown();
            try {
                proceedLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class ThreadModB extends AbstractModule<Time> {
        volatile String updateThreadName;
        final CountDownLatch arrivedLatch;
        final CountDownLatch proceedLatch;

        ThreadModB(CountDownLatch arrivedLatch, CountDownLatch proceedLatch) {
            this.arrivedLatch = arrivedLatch;
            this.proceedLatch = proceedLatch;
        }

        @Override
        protected void doUpdate(Time ctx) {
            updateThreadName = Thread.currentThread().getName();
            arrivedLatch.countDown();
            try {
                proceedLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class ThreadModC extends AbstractModule<Time> {
        volatile String updateThreadName;
        final CountDownLatch arrivedLatch;
        final CountDownLatch proceedLatch;

        ThreadModC(CountDownLatch arrivedLatch, CountDownLatch proceedLatch) {
            this.arrivedLatch = arrivedLatch;
            this.proceedLatch = proceedLatch;
        }

        @Override
        protected void doUpdate(Time ctx) {
            updateThreadName = Thread.currentThread().getName();
            arrivedLatch.countDown();
            try {
                proceedLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class OrderTrackingModuleA extends AbstractModule<Time> {
        final List<String> updateOrder;

        OrderTrackingModuleA(List<String> updateOrder) {
            this.updateOrder = updateOrder;
        }

        @Override
        protected void doUpdate(Time ctx) {
            updateOrder.add("A");
        }
    }

    private static class OrderTrackingModuleB extends AbstractModule<Time> {
        final List<String> updateOrder;

        OrderTrackingModuleB(List<String> updateOrder) {
            this.updateOrder = updateOrder;
        }

        @Override
        protected void doUpdate(Time ctx) {
            updateOrder.add("B");
        }

        @Override
        public Set<Class<? extends Module<Time>>> getDependencies() {
            return Set.of(OrderTrackingModuleA.class);
        }
    }

    private ModuleManager<Time> manager;

    @BeforeEach
    void setUp() {
        manager = new ModuleManager<>(new ManualUpdate<>(), Runnable::run);
    }

    @Nested
    class LifecycleTests {

        @Test
        void addRunsCreateAndInit() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);

            assertEquals(List.of("create", "init"), moduleA.events);
            assertEquals(ModuleState.RUNNING, moduleA.getState());
        }

        @Test
        void moduleInRunningStateAfterAdd() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);

            assertEquals(ModuleState.RUNNING, moduleA.getState());
        }

        @Test
        void managerReferenceAvailableAfterInit() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);

            LookupModule lookup = new LookupModule();
            manager.add(lookup);

            assertNotNull(lookup.foundDep);
            assertEquals(moduleA, lookup.foundDep);
        }

        @Test
        void getModuleReturnsCorrectInstance() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);

            ModuleA retrieved = manager.getModule(ModuleA.class);
            assertEquals(moduleA, retrieved);
        }

        @Test
        void hasModuleReturnsTrueAfterAdd() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);

            assertTrue(manager.hasModule(ModuleA.class));
        }

        @Test
        void hasModuleReturnsFalseForUnregistered() {
            assertFalse(manager.hasModule(ModuleA.class));
        }

        @Test
        void getModulesReturnsAllRegistered() {
            manager.add(new ModuleA());
            manager.add(new IndependentModule());

            ModuleB moduleB = new ModuleB();
            manager.add(moduleB);

            assertEquals(3, manager.getModules().size());
        }
    }

    @Nested
    class ShutdownTests {

        @Test
        void shutdownRunsDeinitAndCleanupInReverseOrder() {
            ModuleA moduleA = new ModuleA();
            ModuleB moduleB = new ModuleB();
            ModuleC moduleC = new ModuleC();

            manager.add(moduleA);
            manager.add(moduleB);
            manager.add(moduleC);

            moduleA.events.clear();
            moduleB.events.clear();
            moduleC.events.clear();

            manager.shutdown();

            assertEquals(List.of("deinit", "cleanup"), moduleA.events);
            assertEquals(List.of("deinit", "cleanup"), moduleB.events);
            assertEquals(List.of("deinit", "cleanup"), moduleC.events);

            assertEquals(ModuleState.CLEANED_UP, moduleA.getState());
            assertEquals(ModuleState.CLEANED_UP, moduleB.getState());
            assertEquals(ModuleState.CLEANED_UP, moduleC.getState());
        }

        @Test
        void shutdownWithChainVerifiesReverseOrder() {
            List<String> globalCleanupOrder = Collections.synchronizedList(new ArrayList<>());

            ModuleA moduleA = new ModuleA();
            moduleA.lifecycleCallback = event -> {
                if ("deinit".equals(event)) {
                    globalCleanupOrder.add("A");
                }
            };

            ModuleB moduleB = new ModuleB();
            moduleB.lifecycleCallback = event -> {
                if ("deinit".equals(event)) {
                    globalCleanupOrder.add("B");
                }
            };

            ModuleC moduleC = new ModuleC();
            moduleC.lifecycleCallback = event -> {
                if ("deinit".equals(event)) {
                    globalCleanupOrder.add("C");
                }
            };

            ModuleD moduleD = new ModuleD();
            moduleD.lifecycleCallback = event -> {
                if ("deinit".equals(event)) {
                    globalCleanupOrder.add("D");
                }
            };

            manager.add(moduleA);
            manager.add(moduleB);
            manager.add(moduleC);
            manager.add(moduleD);

            manager.shutdown();

            int idxD = globalCleanupOrder.indexOf("D");
            int idxB = globalCleanupOrder.indexOf("B");
            int idxC = globalCleanupOrder.indexOf("C");
            int idxA = globalCleanupOrder.indexOf("A");

            assertTrue(idxD < idxB, "D should be cleaned up before B");
            assertTrue(idxD < idxC, "D should be cleaned up before C");
            assertTrue(idxB < idxA, "B should be cleaned up before A");
            assertTrue(idxC < idxA, "C should be cleaned up before A");
        }

        @Test
        void shutdownIdempotent() {
            manager.add(new ModuleA());
            manager.shutdown();

            assertDoesNotThrow(() -> manager.shutdown());
        }

        @Test
        void addAfterShutdownThrows() {
            manager.shutdown();

            assertThrows(IllegalStateException.class, () -> manager.add(new ModuleA()));
        }

        @Test
        void tickAfterShutdownIsNoOp() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);
            moduleA.events.clear();

            manager.shutdown();

            assertDoesNotThrow(() -> manager.tick(1.0));
            assertFalse(moduleA.events.contains("update"));
        }

        @Test
        void updateAfterShutdownIsNoOp() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);
            moduleA.events.clear();

            manager.shutdown();

            assertDoesNotThrow(() -> manager.update(new Time(0, 1.0)));
            assertFalse(moduleA.events.contains("update"));
        }
    }

    @Nested
    class DependencyOrderTests {

        @Test
        void initOrderRespectsDependencies() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);
            assertEquals(ModuleState.RUNNING, moduleA.getState());

            ModuleB moduleB = new ModuleB();
            manager.add(moduleB);
            assertEquals(ModuleState.RUNNING, moduleB.getState());
        }

        @Test
        void addWithMissingDependencyThrows() {
            assertThrows(MissingDependencyException.class, () -> manager.add(new ModuleB()));
        }

        @Test
        void addWithCycleThrows() {
            assertThrows(MissingDependencyException.class, () -> manager.add(new CyclicModuleX()));
        }

        @Test
        void diamondDependencyWorks() {
            manager.add(new ModuleA());
            manager.add(new ModuleB());
            manager.add(new ModuleC());
            manager.add(new ModuleD());

            assertTrue(manager.hasModule(ModuleA.class));
            assertTrue(manager.hasModule(ModuleB.class));
            assertTrue(manager.hasModule(ModuleC.class));
            assertTrue(manager.hasModule(ModuleD.class));

            assertEquals(ModuleState.RUNNING, manager.getModule(ModuleA.class).getState());
            assertEquals(ModuleState.RUNNING, manager.getModule(ModuleB.class).getState());
            assertEquals(ModuleState.RUNNING, manager.getModule(ModuleC.class).getState());
            assertEquals(ModuleState.RUNNING, manager.getModule(ModuleD.class).getState());
        }

        @Test
        void moduleLookupInInit() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);

            LookupModule lookup = new LookupModule();
            manager.add(lookup);

            assertNotNull(lookup.foundDep);
            assertEquals(moduleA, lookup.foundDep);
        }

        @Test
        void duplicateModuleTypeThrows() {
            manager.add(new ModuleA());
            assertThrows(IllegalArgumentException.class, () -> manager.add(new ModuleA()));
        }
    }

    @Nested
    class UpdateTests {

        @Test
        void updateCallsOnUpdateForAllModules() {
            ModuleA moduleA = new ModuleA();
            IndependentModule independent = new IndependentModule();
            manager.add(moduleA);
            manager.add(independent);

            manager.update(new Time(0, 1.0));

            assertTrue(moduleA.events.contains("update"));
            assertTrue(independent.events.contains("update"));
        }

        @Test
        void disabledModuleSkippedDuringUpdate() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);
            moduleA.setEnabled(false);

            Time ctx = new Time(0, 1.0);
            manager.update(ctx);

            assertFalse(moduleA.updateContexts.contains(ctx));
            long updateCount = moduleA.events.stream().filter("update"::equals).count();
            assertEquals(0, updateCount);
        }

        @Test
        void updateContextPassedCorrectly() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);

            Time ctx = new Time(42, 0.016);
            manager.update(ctx);

            assertEquals(1, moduleA.updateContexts.size());
            assertEquals(ctx, moduleA.updateContexts.get(0));
        }

        @Test
        void multipleUpdatesAccumulate() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);

            Time ctx1 = new Time(0, 1.0);
            Time ctx2 = new Time(1, 2.0);
            Time ctx3 = new Time(2, 3.0);
            manager.update(ctx1);
            manager.update(ctx2);
            manager.update(ctx3);

            assertEquals(3, moduleA.updateContexts.size());
            assertEquals(ctx1, moduleA.updateContexts.get(0));
            assertEquals(ctx2, moduleA.updateContexts.get(1));
            assertEquals(ctx3, moduleA.updateContexts.get(2));
        }
    }

    @Nested
    class UpdateStrategyIntegrationTests {

        @Test
        void tickWithFixedTimestepFiresCorrectUpdates() {
            ModuleManager<Time> fixedManager = new ModuleManager<>(
                    new FixedTimestep<>(10.0, Time::new, 5),
                    Runnable::run
            );

            ModuleA moduleA = new ModuleA();
            fixedManager.add(moduleA);
            moduleA.events.clear();
            moduleA.updateContexts.clear();

            fixedManager.tick(0.25);

            assertEquals(2, moduleA.updateContexts.size());
            assertEquals(0.1, moduleA.updateContexts.get(0).timeDelta(), 0.001);
            assertEquals(0.1, moduleA.updateContexts.get(1).timeDelta(), 0.001);
        }

        @Test
        void tickWithVariableTimestep() {
            ModuleManager<Time> varManager = new ModuleManager<>(
                    new VariableTimestep<>(Time::new),
                    Runnable::run
            );

            ModuleA moduleA = new ModuleA();
            varManager.add(moduleA);
            moduleA.events.clear();
            moduleA.updateContexts.clear();

            varManager.tick(0.016);

            assertEquals(1, moduleA.updateContexts.size());
            assertEquals(0.016, moduleA.updateContexts.get(0).timeDelta(), 0.001);
        }

        @Test
        void manualUpdateTickIsNoOp() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);
            moduleA.events.clear();

            manager.tick(1.0);

            assertFalse(moduleA.events.contains("update"));
            assertTrue(moduleA.updateContexts.isEmpty());
        }

        @Test
        void manualUpdateDirectUpdateWorks() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);
            moduleA.events.clear();
            moduleA.updateContexts.clear();

            Time ctx = new Time(5, 5.0);
            manager.update(ctx);

            assertEquals(1, moduleA.updateContexts.size());
            assertEquals(ctx, moduleA.updateContexts.get(0));
        }
    }

    @Nested
    class ParallelExecutionTests {

        @Test
        void independentModulesRunOnDifferentThreads() throws Exception {
            ExecutorService pool = Executors.newFixedThreadPool(3);
            try {
                ModuleManager<Time> parallelManager = new ModuleManager<>(
                        new ManualUpdate<>(), pool
                );

                CountDownLatch arrivedLatch = new CountDownLatch(3);
                CountDownLatch proceedLatch = new CountDownLatch(1);

                ThreadModA modA = new ThreadModA(arrivedLatch, proceedLatch);
                ThreadModB modB = new ThreadModB(arrivedLatch, proceedLatch);
                ThreadModC modC = new ThreadModC(arrivedLatch, proceedLatch);

                parallelManager.add(modA);
                parallelManager.add(modB);
                parallelManager.add(modC);

                Thread updateThread = new Thread(() -> parallelManager.update(new Time(0, 1.0)));
                updateThread.start();

                boolean allArrived = arrivedLatch.await(5, TimeUnit.SECONDS);
                assertTrue(allArrived, "All 3 modules should start executing concurrently");

                proceedLatch.countDown();
                updateThread.join(5000);

                assertNotNull(modA.updateThreadName);
                assertNotNull(modB.updateThreadName);
                assertNotNull(modC.updateThreadName);

                Set<String> threadNames = ConcurrentHashMap.newKeySet();
                threadNames.add(modA.updateThreadName);
                threadNames.add(modB.updateThreadName);
                threadNames.add(modC.updateThreadName);

                assertTrue(threadNames.size() >= 2,
                        "Independent modules should run on at least 2 different threads, got: "
                                + threadNames);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void dependencyLevelsRespected() {
            List<String> updateOrder = Collections.synchronizedList(new ArrayList<>());

            OrderTrackingModuleA modA = new OrderTrackingModuleA(updateOrder);
            OrderTrackingModuleB modB = new OrderTrackingModuleB(updateOrder);

            manager.add(modA);
            manager.add(modB);

            manager.update(new Time(0, 1.0));

            assertEquals(List.of("A", "B"), updateOrder);
        }
    }

    @Nested
    class RuntimeRemovalTests {

        @Test
        void removeWithRejectNoDependentsSucceeds() {
            ModuleA moduleA = new ModuleA();
            manager.add(moduleA);

            manager.remove(ModuleA.class, RemovalPolicy.REJECT);

            assertFalse(manager.hasModule(ModuleA.class));
            assertEquals(ModuleState.CLEANED_UP, moduleA.getState());
        }

        @Test
        void removeWithRejectHasDependentsThrows() {
            manager.add(new ModuleA());
            manager.add(new ModuleB());

            assertThrows(DependentModuleException.class,
                    () -> manager.remove(ModuleA.class, RemovalPolicy.REJECT));

            assertTrue(manager.hasModule(ModuleA.class));
        }

        @Test
        void removeWithCascadeRemovesDependents() {
            manager.add(new ModuleA());
            manager.add(new ModuleB());
            manager.add(new ModuleC());

            manager.remove(ModuleA.class, RemovalPolicy.CASCADE);

            assertFalse(manager.hasModule(ModuleA.class));
            assertFalse(manager.hasModule(ModuleB.class));
            assertFalse(manager.hasModule(ModuleC.class));
        }

        @Test
        void removeWithCascadeRunsCleanupInOrder() {
            List<String> cleanupOrder = Collections.synchronizedList(new ArrayList<>());

            ModuleA moduleA = new ModuleA();
            moduleA.lifecycleCallback = event -> {
                if ("cleanup".equals(event)) {
                    cleanupOrder.add("A");
                }
            };

            ModuleB moduleB = new ModuleB();
            moduleB.lifecycleCallback = event -> {
                if ("cleanup".equals(event)) {
                    cleanupOrder.add("B");
                }
            };

            ModuleC moduleC = new ModuleC();
            moduleC.lifecycleCallback = event -> {
                if ("cleanup".equals(event)) {
                    cleanupOrder.add("C");
                }
            };

            ModuleD moduleD = new ModuleD();
            moduleD.lifecycleCallback = event -> {
                if ("cleanup".equals(event)) {
                    cleanupOrder.add("D");
                }
            };

            manager.add(moduleA);
            manager.add(moduleB);
            manager.add(moduleC);
            manager.add(moduleD);

            manager.remove(ModuleA.class, RemovalPolicy.CASCADE);

            int idxD = cleanupOrder.indexOf("D");
            int idxB = cleanupOrder.indexOf("B");
            int idxC = cleanupOrder.indexOf("C");
            int idxA = cleanupOrder.indexOf("A");

            assertTrue(idxD >= 0, "D should be in cleanup order");
            assertTrue(idxB >= 0, "B should be in cleanup order");
            assertTrue(idxC >= 0, "C should be in cleanup order");
            assertTrue(idxA >= 0, "A should be in cleanup order");

            assertTrue(idxD < idxB, "D should be cleaned up before B");
            assertTrue(idxD < idxC, "D should be cleaned up before C");
            assertTrue(idxB < idxA, "B should be cleaned up before A");
            assertTrue(idxC < idxA, "C should be cleaned up before A");
        }

        @Test
        void removedModuleNotFoundByGetModule() {
            manager.add(new ModuleA());
            manager.remove(ModuleA.class, RemovalPolicy.REJECT);

            assertNull(manager.getModule(ModuleA.class));
        }

        @Test
        void canAddNewModuleAfterRemoval() {
            ModuleA first = new ModuleA();
            manager.add(first);
            manager.remove(ModuleA.class, RemovalPolicy.REJECT);

            ModuleA second = new ModuleA();
            manager.add(second);

            assertTrue(manager.hasModule(ModuleA.class));
            assertEquals(second, manager.getModule(ModuleA.class));
            assertEquals(ModuleState.RUNNING, second.getState());
        }

        @Test
        void removeNonExistentModuleThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.remove(ModuleA.class, RemovalPolicy.REJECT));
        }
    }

    @Nested
    class ErrorHandlingTests {

        @Test
        void exceptionInOnUpdateDoesNotCrashOthers() {
            FailingUpdateModule failingModule = new FailingUpdateModule();
            ModuleA moduleA = new ModuleA();

            manager.add(failingModule);
            manager.add(moduleA);

            Time ctx = new Time(0, 1.0);
            assertDoesNotThrow(() -> manager.update(ctx));

            assertTrue(moduleA.updateContexts.contains(ctx));
        }

        @Test
        void exceptionInOnUpdateIsLogged() {
            FailingUpdateModule failingModule = new FailingUpdateModule();
            manager.add(failingModule);
            failingModule.events.clear();

            Time ctx = new Time(0, 1.0);
            assertDoesNotThrow(() -> manager.update(ctx));

            assertTrue(failingModule.events.contains("update"));
            assertTrue(failingModule.updateContexts.contains(ctx));
        }
    }

    @Nested
    class SharedExecutorTests {

        @Test
        void twoManagersShareExecutor() {
            ModuleManager<Time> manager1 = new ModuleManager<>(new ManualUpdate<>(), Runnable::run);
            ModuleManager<Time> manager2 = new ModuleManager<>(new ManualUpdate<>(), Runnable::run);

            ModuleA mod1 = new ModuleA();
            IndependentModule mod2 = new IndependentModule();

            manager1.add(mod1);
            manager2.add(mod2);

            Time ctx1 = new Time(0, 1.0);
            Time ctx2 = new Time(0, 2.0);
            manager1.update(ctx1);
            manager2.update(ctx2);

            assertEquals(1, mod1.updateContexts.size());
            assertEquals(ctx1, mod1.updateContexts.get(0));
            assertEquals(1, mod2.updateContexts.size());
            assertEquals(ctx2, mod2.updateContexts.get(0));
        }

        @Test
        void shutdownOneManagerDoesNotAffectOther() {
            ModuleManager<Time> manager1 = new ModuleManager<>(new ManualUpdate<>(), Runnable::run);
            ModuleManager<Time> manager2 = new ModuleManager<>(new ManualUpdate<>(), Runnable::run);

            manager1.add(new ModuleA());
            IndependentModule mod2 = new IndependentModule();
            manager2.add(mod2);

            manager1.shutdown();

            Time ctx = new Time(0, 3.0);
            manager2.update(ctx);
            assertEquals(1, mod2.updateContexts.size());
            assertEquals(ctx, mod2.updateContexts.get(0));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyManagerTickDoesNotThrow() {
            assertDoesNotThrow(() -> manager.tick(1.0));
        }

        @Test
        void emptyManagerShutdownDoesNotThrow() {
            assertDoesNotThrow(() -> manager.shutdown());
        }

        @Test
        void emptyManagerUpdateDoesNotThrow() {
            assertDoesNotThrow(() -> manager.update(new Time(0, 1.0)));
        }

        @Test
        void getModuleForNonExistentReturnsNull() {
            assertNull(manager.getModule(ModuleA.class));
        }

        @Test
        void getModulesReturnsUnmodifiableCollection() {
            manager.add(new ModuleA());

            Collection<Module<Time>> modules = manager.getModules();

            assertThrows(UnsupportedOperationException.class,
                    () -> modules.add(new IndependentModule()));
        }
    }
}
