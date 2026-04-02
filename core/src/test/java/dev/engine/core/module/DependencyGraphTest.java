package dev.engine.core.module;

import dev.engine.core.module.exception.CyclicDependencyException;
import dev.engine.core.module.exception.MissingDependencyException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyGraphTest {

    private static class ModA {}
    private static class ModB {}
    private static class ModC {}
    private static class ModD {}
    private static class ModE {}
    private static class ModF {}
    private static class ModG {}
    private static class ModH {}
    private static class ModI {}
    private static class ModJ {}
    private static class ModK {}
    private static class ModL {}

    private DependencyGraph graph;

    @BeforeEach
    void setUp() {
        graph = new DependencyGraph();
    }

    @Nested
    class BasicOperations {

        @Test
        void addSingleModuleNoDeps() {
            graph.add(ModA.class, Set.of());

            assertTrue(graph.contains(ModA.class));
            assertEquals(1, graph.getAll().size());
            assertTrue(graph.getAll().contains(ModA.class));
        }

        @Test
        void addMultipleIndependentModules() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of());
            graph.add(ModC.class, Set.of());

            assertTrue(graph.contains(ModA.class));
            assertTrue(graph.contains(ModB.class));
            assertTrue(graph.contains(ModC.class));
            assertEquals(3, graph.getAll().size());
        }

        @Test
        void addDuplicateThrows() {
            graph.add(ModA.class, Set.of());

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> graph.add(ModA.class, Set.of()));

            assertTrue(ex.getMessage().contains("ModA"));
        }

        @Test
        void removeModule() {
            graph.add(ModA.class, Set.of());
            assertTrue(graph.contains(ModA.class));

            graph.remove(ModA.class);

            assertFalse(graph.contains(ModA.class));
            assertEquals(0, graph.getAll().size());
        }

        @Test
        void removeNonExistentIsNoOp() {
            assertDoesNotThrow(() -> graph.remove(ModA.class));
        }

        @Test
        void containsReturnsFalseForUnknown() {
            assertFalse(graph.contains(ModA.class));
        }
    }

    @Nested
    class TopologicalOrdering {

        @Test
        void singleModuleInitOrder() {
            graph.add(ModA.class, Set.of());

            List<Class<?>> order = graph.getInitOrder();

            assertEquals(1, order.size());
            assertEquals(ModA.class, order.getFirst());
        }

        @Test
        void linearChainInitOrder() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModB.class));

            List<Class<?>> order = graph.getInitOrder();

            assertEquals(3, order.size());
            assertEquals(ModA.class, order.get(0));
            assertEquals(ModB.class, order.get(1));
            assertEquals(ModC.class, order.get(2));
        }

        @Test
        void diamondInitOrder() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModA.class));
            graph.add(ModD.class, Set.of(ModB.class, ModC.class));

            List<Class<?>> order = graph.getInitOrder();

            assertEquals(4, order.size());
            int posA = order.indexOf(ModA.class);
            int posB = order.indexOf(ModB.class);
            int posC = order.indexOf(ModC.class);
            int posD = order.indexOf(ModD.class);

            assertTrue(posA < posB, "A must come before B");
            assertTrue(posA < posC, "A must come before C");
            assertTrue(posB < posD, "B must come before D");
            assertTrue(posC < posD, "C must come before D");
        }

        @Test
        void independentModulesInitOrder() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of());
            graph.add(ModC.class, Set.of());

            List<Class<?>> order = graph.getInitOrder();

            assertEquals(3, order.size());
            assertTrue(order.contains(ModA.class));
            assertTrue(order.contains(ModB.class));
            assertTrue(order.contains(ModC.class));
        }

        @Test
        void emptyGraphInitOrder() {
            List<Class<?>> order = graph.getInitOrder();

            assertTrue(order.isEmpty());
        }

        @Test
        void initOrderIsValidTopologicalSort() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModA.class));
            graph.add(ModD.class, Set.of(ModB.class, ModC.class));
            graph.add(ModE.class, Set.of(ModD.class));
            graph.add(ModF.class, Set.of(ModC.class));

            List<Class<?>> order = graph.getInitOrder();

            assertValidTopologicalOrder(order);
        }
    }

    @Nested
    class CleanupOrder {

        @Test
        void cleanupOrderIsReverseOfInit() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModB.class));

            List<Class<?>> initOrder = graph.getInitOrder();
            List<Class<?>> cleanupOrder = graph.getCleanupOrder();

            assertEquals(3, cleanupOrder.size());
            assertEquals(initOrder.get(0), cleanupOrder.get(2));
            assertEquals(initOrder.get(1), cleanupOrder.get(1));
            assertEquals(initOrder.get(2), cleanupOrder.get(0));
        }

        @Test
        void diamondCleanupOrder() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModA.class));
            graph.add(ModD.class, Set.of(ModB.class, ModC.class));

            List<Class<?>> cleanupOrder = graph.getCleanupOrder();

            assertEquals(4, cleanupOrder.size());
            int posA = cleanupOrder.indexOf(ModA.class);
            int posB = cleanupOrder.indexOf(ModB.class);
            int posC = cleanupOrder.indexOf(ModC.class);
            int posD = cleanupOrder.indexOf(ModD.class);

            assertTrue(posD < posB, "D must come before B in cleanup");
            assertTrue(posD < posC, "D must come before C in cleanup");
            assertTrue(posB < posA, "B must come before A in cleanup");
            assertTrue(posC < posA, "C must come before A in cleanup");
        }
    }

    @Nested
    class ParallelLevels {

        @Test
        void singleModuleOneLevel() {
            graph.add(ModA.class, Set.of());

            List<Set<Class<?>>> levels = graph.getParallelLevels();

            assertEquals(1, levels.size());
            assertEquals(Set.of(ModA.class), levels.getFirst());
        }

        @Test
        void linearChainLevels() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModB.class));

            List<Set<Class<?>>> levels = graph.getParallelLevels();

            assertEquals(3, levels.size());
            assertEquals(Set.of(ModA.class), levels.get(0));
            assertEquals(Set.of(ModB.class), levels.get(1));
            assertEquals(Set.of(ModC.class), levels.get(2));
        }

        @Test
        void diamondLevels() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModA.class));
            graph.add(ModD.class, Set.of(ModB.class, ModC.class));

            List<Set<Class<?>>> levels = graph.getParallelLevels();

            assertEquals(3, levels.size());
            assertEquals(Set.of(ModA.class), levels.get(0));
            assertEquals(Set.of(ModB.class, ModC.class), levels.get(1));
            assertEquals(Set.of(ModD.class), levels.get(2));
        }

        @Test
        void independentModulesOneLevel() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of());
            graph.add(ModC.class, Set.of());

            List<Set<Class<?>>> levels = graph.getParallelLevels();

            assertEquals(1, levels.size());
            assertEquals(Set.of(ModA.class, ModB.class, ModC.class), levels.getFirst());
        }

        @Test
        void complexGraphLevels() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of());
            graph.add(ModC.class, Set.of(ModA.class));
            graph.add(ModD.class, Set.of(ModB.class));
            graph.add(ModE.class, Set.of(ModC.class, ModD.class));
            graph.add(ModF.class, Set.of(ModE.class));

            List<Set<Class<?>>> levels = graph.getParallelLevels();

            assertEquals(4, levels.size());
            assertEquals(Set.of(ModA.class, ModB.class), levels.get(0));
            assertEquals(Set.of(ModC.class, ModD.class), levels.get(1));
            assertEquals(Set.of(ModE.class), levels.get(2));
            assertEquals(Set.of(ModF.class), levels.get(3));
        }

        @Test
        void emptyGraphParallelLevels() {
            List<Set<Class<?>>> levels = graph.getParallelLevels();

            assertTrue(levels.isEmpty());
        }
    }

    @Nested
    class CycleDetection {

        @Test
        void selfDependencyThrows() {
            assertThrows(CyclicDependencyException.class,
                    () -> graph.add(ModA.class, Set.of(ModA.class)));
        }

        @Test
        void selfDependencyDoesNotRegisterModule() {
            try {
                graph.add(ModA.class, Set.of(ModA.class));
            } catch (CyclicDependencyException ignored) {
            }

            assertFalse(graph.contains(ModA.class));
        }

        @Test
        void directCycleThrows() {
            graph.add(ModA.class, Set.of(ModB.class));

            assertThrows(CyclicDependencyException.class,
                    () -> graph.add(ModB.class, Set.of(ModA.class)));
        }

        @Test
        void indirectCycleThrows() {
            graph.add(ModA.class, Set.of(ModC.class));
            graph.add(ModB.class, Set.of(ModA.class));

            assertThrows(CyclicDependencyException.class,
                    () -> graph.add(ModC.class, Set.of(ModB.class)));
        }

        @Test
        void cycleRollsBackAddition() {
            graph.add(ModA.class, Set.of(ModB.class));

            try {
                graph.add(ModB.class, Set.of(ModA.class));
            } catch (CyclicDependencyException ignored) {
            }

            assertFalse(graph.contains(ModB.class));
            assertTrue(graph.contains(ModA.class));
            assertEquals(1, graph.getAll().size());
        }

        @Test
        void cyclicExceptionContainsCyclePath() {
            graph.add(ModA.class, Set.of(ModB.class));

            CyclicDependencyException ex = assertThrows(CyclicDependencyException.class,
                    () -> graph.add(ModB.class, Set.of(ModA.class)));

            List<String> cyclePath = ex.getCyclePath();
            assertFalse(cyclePath.isEmpty(), "Cycle path should not be empty");
            assertEquals(cyclePath.getFirst(), cyclePath.getLast(),
                    "Cycle path should start and end with the same module");
        }

        @Test
        void noCycleWithUnregisteredDependency() {
            assertDoesNotThrow(() -> graph.add(ModA.class, Set.of(ModB.class)));
            assertTrue(graph.contains(ModA.class));
        }

        @Test
        void graphRemainsUsableAfterCycleRollback() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));

            try {
                graph.add(ModC.class, Set.of(ModC.class));
            } catch (CyclicDependencyException ignored) {
            }

            assertFalse(graph.contains(ModC.class));

            assertDoesNotThrow(() -> graph.add(ModC.class, Set.of(ModB.class)));

            assertTrue(graph.contains(ModC.class));
            assertEquals(3, graph.getAll().size());

            List<Class<?>> order = graph.getInitOrder();
            assertEquals(3, order.size());
            assertValidTopologicalOrder(order);
        }
    }

    @Nested
    class Validation {

        @Test
        void validateWithMissingDependency() {
            graph.add(ModA.class, Set.of(ModB.class));

            MissingDependencyException ex = assertThrows(MissingDependencyException.class,
                    () -> graph.validate());

            assertEquals(ModA.class, ex.getRequestingModule());
            assertEquals(ModB.class, ex.getMissingDependency());
        }

        @Test
        void validateCleanGraph() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModA.class, ModB.class));

            assertDoesNotThrow(() -> graph.validate());
        }

        @Test
        void validateEmptyGraph() {
            assertDoesNotThrow(() -> graph.validate());
        }
    }

    @Nested
    class DependentsQueries {

        @Test
        void directDependents() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));

            Set<Class<?>> dependents = graph.getDependents(ModA.class);

            assertEquals(1, dependents.size());
            assertTrue(dependents.contains(ModB.class));
        }

        @Test
        void noDependents() {
            graph.add(ModA.class, Set.of());

            Set<Class<?>> dependents = graph.getDependents(ModA.class);

            assertTrue(dependents.isEmpty());
        }

        @Test
        void multipleDependents() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModA.class));

            Set<Class<?>> dependents = graph.getDependents(ModA.class);

            assertEquals(2, dependents.size());
            assertTrue(dependents.contains(ModB.class));
            assertTrue(dependents.contains(ModC.class));
        }

        @Test
        void transitiveDependents() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModB.class));

            Set<Class<?>> transitive = graph.getTransitiveDependents(ModA.class);

            assertEquals(2, transitive.size());
            assertTrue(transitive.contains(ModB.class));
            assertTrue(transitive.contains(ModC.class));
        }

        @Test
        void transitiveDependentsDiamond() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModA.class));
            graph.add(ModD.class, Set.of(ModB.class, ModC.class));

            Set<Class<?>> transitive = graph.getTransitiveDependents(ModA.class);

            assertEquals(3, transitive.size());
            assertTrue(transitive.contains(ModB.class));
            assertTrue(transitive.contains(ModC.class));
            assertTrue(transitive.contains(ModD.class));
        }

        @Test
        void transitiveDependentsExcludesQueriedModule() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));

            Set<Class<?>> transitive = graph.getTransitiveDependents(ModA.class);

            assertFalse(transitive.contains(ModA.class));
        }

        @Test
        void transitiveDependentsOfLeafModule() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));

            Set<Class<?>> transitive = graph.getTransitiveDependents(ModB.class);

            assertTrue(transitive.isEmpty());
        }

        @Test
        void transitiveDependentsOfUnregisteredModule() {
            Set<Class<?>> transitive = graph.getTransitiveDependents(ModA.class);

            assertTrue(transitive.isEmpty());
        }
    }

    @Nested
    class DependenciesQuery {

        @Test
        void getDependenciesReturnsDirectDeps() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));

            Set<Class<?>> deps = graph.getDependencies(ModB.class);

            assertEquals(1, deps.size());
            assertTrue(deps.contains(ModA.class));
        }

        @Test
        void getDependenciesUnregisteredReturnsEmpty() {
            Set<Class<?>> deps = graph.getDependencies(ModA.class);

            assertTrue(deps.isEmpty());
        }

        @Test
        void getDependenciesNoDeps() {
            graph.add(ModA.class, Set.of());

            Set<Class<?>> deps = graph.getDependencies(ModA.class);

            assertTrue(deps.isEmpty());
        }

        @Test
        void getDependenciesMultipleDeps() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of());
            graph.add(ModC.class, Set.of(ModA.class, ModB.class));

            Set<Class<?>> deps = graph.getDependencies(ModC.class);

            assertEquals(2, deps.size());
            assertTrue(deps.contains(ModA.class));
            assertTrue(deps.contains(ModB.class));
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void emptyGraphOperations() {
            assertTrue(graph.getInitOrder().isEmpty());
            assertTrue(graph.getCleanupOrder().isEmpty());
            assertTrue(graph.getParallelLevels().isEmpty());
            assertTrue(graph.getAll().isEmpty());
        }

        @Test
        void removeAndReAdd() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));

            graph.remove(ModB.class);
            assertFalse(graph.contains(ModB.class));

            graph.add(ModB.class, Set.of());

            assertTrue(graph.contains(ModB.class));
            assertTrue(graph.getDependencies(ModB.class).isEmpty());
        }

        @Test
        void removeModuleWithDependentsUpdatesDependentsMap() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));

            graph.remove(ModB.class);

            assertTrue(graph.getDependents(ModA.class).isEmpty());
        }

        @Test
        void getAllReturnsUnmodifiableCopy() {
            graph.add(ModA.class, Set.of());

            Set<Class<?>> all = graph.getAll();

            assertThrows(UnsupportedOperationException.class,
                    () -> all.add(ModB.class));
        }

        @Test
        void getDependenciesReturnsUnmodifiable() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));

            Set<Class<?>> deps = graph.getDependencies(ModB.class);

            assertThrows(UnsupportedOperationException.class,
                    () -> deps.add(ModC.class));
        }

        @Test
        void getDependentsReturnsUnmodifiable() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));

            Set<Class<?>> deps = graph.getDependents(ModA.class);

            assertThrows(UnsupportedOperationException.class,
                    () -> deps.add(ModC.class));
        }

        @Test
        void largeGraph() {
            Class<?>[] modules = {
                    ModA.class, ModB.class, ModC.class, ModD.class,
                    ModE.class, ModF.class, ModG.class, ModH.class,
                    ModI.class, ModJ.class, ModK.class, ModL.class
            };

            graph.add(modules[0], Set.of());
            for (int i = 1; i < modules.length; i++) {
                graph.add(modules[i], Set.of(modules[i - 1]));
            }

            List<Class<?>> order = graph.getInitOrder();

            assertEquals(modules.length, order.size());
            assertValidTopologicalOrder(order);

            for (int i = 0; i < modules.length; i++) {
                assertEquals(modules[i], order.get(i));
            }
        }

        @Test
        void largeGraphWithBranching() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModA.class));
            graph.add(ModD.class, Set.of(ModB.class));
            graph.add(ModE.class, Set.of(ModC.class));
            graph.add(ModF.class, Set.of(ModD.class, ModE.class));
            graph.add(ModG.class, Set.of(ModF.class));
            graph.add(ModH.class, Set.of(ModF.class));
            graph.add(ModI.class, Set.of(ModG.class, ModH.class));
            graph.add(ModJ.class, Set.of(ModI.class));

            List<Class<?>> order = graph.getInitOrder();

            assertEquals(10, order.size());
            assertValidTopologicalOrder(order);
        }

        @Test
        void removeMiddleOfChainAndRevalidate() {
            graph.add(ModA.class, Set.of());
            graph.add(ModB.class, Set.of(ModA.class));
            graph.add(ModC.class, Set.of(ModB.class));

            graph.remove(ModB.class);

            assertThrows(MissingDependencyException.class, () -> graph.validate());

            List<Class<?>> order = graph.getInitOrder();
            assertEquals(2, order.size());
            assertTrue(order.contains(ModA.class));
            assertTrue(order.contains(ModC.class));
        }

        @Test
        void addModuleWithUnregisteredDepThenRegisterDep() {
            graph.add(ModA.class, Set.of(ModB.class));

            assertThrows(MissingDependencyException.class, () -> graph.validate());

            graph.add(ModB.class, Set.of());

            assertDoesNotThrow(() -> graph.validate());

            List<Class<?>> order = graph.getInitOrder();
            assertTrue(order.indexOf(ModB.class) < order.indexOf(ModA.class),
                    "B should come before A since A depends on B");
        }
    }

    private void assertValidTopologicalOrder(List<Class<?>> order) {
        Set<Class<?>> seen = new HashSet<>();
        for (Class<?> module : order) {
            Set<Class<?>> deps = graph.getDependencies(module);
            for (Class<?> dep : deps) {
                if (graph.contains(dep)) {
                    assertTrue(seen.contains(dep),
                            "Dependency " + dep.getSimpleName()
                                    + " must appear before " + module.getSimpleName()
                                    + " in topological order");
                }
            }
            seen.add(module);
        }
    }
}
