package dev.engine.core.module;

import dev.engine.core.module.exception.CyclicDependencyException;
import dev.engine.core.module.exception.MissingDependencyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages the directed acyclic graph of module dependencies.
 *
 * <p>Nodes are module classes ({@code Class<?>}) and edges represent "depends-on"
 * relationships. The graph supports topological sorting via Kahn's algorithm,
 * cycle detection, parallel-level computation, and transitive-dependent queries.
 *
 * <p>This class is package-private — only {@link ModuleManager} should use it directly.
 * All public methods are synchronized for thread safety.
 */
class DependencyGraph {

    private static final Logger log = LoggerFactory.getLogger(DependencyGraph.class);

    /** Adjacency list: module -> set of modules it depends on. */
    private final Map<Class<?>, Set<Class<?>>> dependencies = new LinkedHashMap<>();

    /** Reverse adjacency: module -> set of modules that depend on it. */
    private final Map<Class<?>, Set<Class<?>>> dependents = new LinkedHashMap<>();

    synchronized void add(Class<?> moduleType, Set<Class<?>> deps) {
        if (dependencies.containsKey(moduleType)) {
            throw new IllegalArgumentException(
                    "Module already registered: " + moduleType.getSimpleName());
        }
        if (deps.contains(moduleType)) {
            List<String> cyclePath = List.of(
                    moduleType.getSimpleName(), moduleType.getSimpleName());
            throw new CyclicDependencyException(cyclePath);
        }

        // Add forward edges
        dependencies.put(moduleType, new LinkedHashSet<>(deps));

        // Add reverse edges
        dependents.putIfAbsent(moduleType, new LinkedHashSet<>());
        for (Class<?> dep : deps) {
            dependents.computeIfAbsent(dep, k -> new LinkedHashSet<>()).add(moduleType);
        }

        // Check for cycles — if found, undo and throw
        List<String> cyclePath = detectCycle();
        if (cyclePath != null) {
            undoAdd(moduleType, deps);
            throw new CyclicDependencyException(cyclePath);
        }

        log.debug("Added module {} with dependencies {}",
                moduleType.getSimpleName(),
                deps.stream().map(Class::getSimpleName).toList());
    }

    synchronized void remove(Class<?> moduleType) {
        Set<Class<?>> deps = dependencies.remove(moduleType);
        if (deps != null) {
            for (Class<?> dep : deps) {
                Set<Class<?>> depDependents = dependents.get(dep);
                if (depDependents != null) {
                    depDependents.remove(moduleType);
                }
            }
        }
        dependents.remove(moduleType);
        log.debug("Removed module {}", moduleType.getSimpleName());
    }

    synchronized boolean contains(Class<?> moduleType) {
        return dependencies.containsKey(moduleType);
    }

    synchronized Set<Class<?>> getAll() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(dependencies.keySet()));
    }

    synchronized List<Class<?>> getInitOrder() {
        return kahnSort();
    }

    synchronized List<Class<?>> getCleanupOrder() {
        List<Class<?>> initOrder = kahnSort();
        List<Class<?>> reversed = new ArrayList<>(initOrder);
        Collections.reverse(reversed);
        return reversed;
    }

    synchronized List<Set<Class<?>>> getParallelLevels() {
        Map<Class<?>, Integer> inDegree = computeInDegrees();
        Deque<Class<?>> queue = new ArrayDeque<>();

        for (Map.Entry<Class<?>, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<Set<Class<?>>> levels = new ArrayList<>();
        int processed = 0;

        while (!queue.isEmpty()) {
            Set<Class<?>> currentLevel = new LinkedHashSet<>(queue);
            levels.add(Collections.unmodifiableSet(currentLevel));
            queue.clear();

            for (Class<?> node : currentLevel) {
                processed++;
                Set<Class<?>> nodeDependents = dependents.getOrDefault(node, Set.of());
                for (Class<?> dependent : nodeDependents) {
                    if (inDegree.containsKey(dependent)) {
                        int newDegree = inDegree.get(dependent) - 1;
                        inDegree.put(dependent, newDegree);
                        if (newDegree == 0) {
                            queue.add(dependent);
                        }
                    }
                }
            }
        }

        if (processed < dependencies.size()) {
            List<String> cyclePath = findCyclePath(inDegree);
            throw new CyclicDependencyException(cyclePath);
        }

        return levels;
    }

    synchronized Set<Class<?>> getDependencies(Class<?> moduleType) {
        Set<Class<?>> deps = dependencies.get(moduleType);
        if (deps == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(deps));
    }

    synchronized Set<Class<?>> getDependents(Class<?> moduleType) {
        Set<Class<?>> deps = dependents.get(moduleType);
        if (deps == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(deps));
    }

    synchronized Set<Class<?>> getTransitiveDependents(Class<?> moduleType) {
        Set<Class<?>> visited = new LinkedHashSet<>();
        Deque<Class<?>> queue = new ArrayDeque<>();

        Set<Class<?>> directDependents = dependents.getOrDefault(moduleType, Set.of());
        queue.addAll(directDependents);
        visited.addAll(directDependents);

        while (!queue.isEmpty()) {
            Class<?> current = queue.poll();
            Set<Class<?>> currentDependents = dependents.getOrDefault(current, Set.of());
            for (Class<?> dep : currentDependents) {
                if (visited.add(dep)) {
                    queue.add(dep);
                }
            }
        }

        return Collections.unmodifiableSet(visited);
    }

    synchronized void validate() {
        // Check for missing dependencies
        for (Map.Entry<Class<?>, Set<Class<?>>> entry : dependencies.entrySet()) {
            Class<?> moduleType = entry.getKey();
            for (Class<?> dep : entry.getValue()) {
                if (!dependencies.containsKey(dep)) {
                    throw new MissingDependencyException(moduleType, dep);
                }
            }
        }

        // Check for cycles by running Kahn's algorithm
        kahnSort();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private List<Class<?>> kahnSort() {
        Map<Class<?>, Integer> inDegree = computeInDegrees();
        Deque<Class<?>> queue = new ArrayDeque<>();

        for (Map.Entry<Class<?>, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<Class<?>> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Class<?> node = queue.poll();
            result.add(node);

            Set<Class<?>> nodeDependents = dependents.getOrDefault(node, Set.of());
            for (Class<?> dependent : nodeDependents) {
                if (inDegree.containsKey(dependent)) {
                    int newDegree = inDegree.get(dependent) - 1;
                    inDegree.put(dependent, newDegree);
                    if (newDegree == 0) {
                        queue.add(dependent);
                    }
                }
            }
        }

        if (result.size() < dependencies.size()) {
            List<String> cyclePath = findCyclePath(inDegree);
            throw new CyclicDependencyException(cyclePath);
        }

        return result;
    }

    private Map<Class<?>, Integer> computeInDegrees() {
        Map<Class<?>, Integer> inDegree = new HashMap<>();
        for (Class<?> node : dependencies.keySet()) {
            inDegree.put(node, 0);
        }
        for (Map.Entry<Class<?>, Set<Class<?>>> entry : dependencies.entrySet()) {
            int count = 0;
            for (Class<?> dep : entry.getValue()) {
                if (dependencies.containsKey(dep)) {
                    count++;
                }
            }
            inDegree.put(entry.getKey(), count);
        }
        return inDegree;
    }

    private List<String> detectCycle() {
        Map<Class<?>, Integer> inDegree = computeInDegrees();
        Deque<Class<?>> queue = new ArrayDeque<>();

        for (Map.Entry<Class<?>, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        int processed = 0;
        while (!queue.isEmpty()) {
            Class<?> node = queue.poll();
            processed++;

            Set<Class<?>> nodeDependents = dependents.getOrDefault(node, Set.of());
            for (Class<?> dependent : nodeDependents) {
                if (inDegree.containsKey(dependent)) {
                    int newDegree = inDegree.get(dependent) - 1;
                    inDegree.put(dependent, newDegree);
                    if (newDegree == 0) {
                        queue.add(dependent);
                    }
                }
            }
        }

        if (processed < dependencies.size()) {
            return findCyclePath(inDegree);
        }
        return null;
    }

    private List<String> findCyclePath(Map<Class<?>, Integer> inDegree) {
        // Collect nodes that are part of the cycle (in-degree > 0 after Kahn's)
        Set<Class<?>> remaining = new HashSet<>();
        for (Map.Entry<Class<?>, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() > 0) {
                remaining.add(entry.getKey());
            }
        }

        if (remaining.isEmpty()) {
            return List.of("unknown", "unknown");
        }

        // DFS to find the actual cycle path among remaining nodes
        Class<?> start = remaining.iterator().next();
        Map<Class<?>, Class<?>> parent = new HashMap<>();
        Set<Class<?>> visited = new HashSet<>();
        Deque<Class<?>> stack = new ArrayDeque<>();

        stack.push(start);
        visited.add(start);
        parent.put(start, null);

        Class<?> cycleEnd = null;

        outer:
        while (!stack.isEmpty()) {
            Class<?> current = stack.peek();
            boolean expanded = false;

            Set<Class<?>> deps = dependencies.getOrDefault(current, Set.of());
            for (Class<?> dep : deps) {
                if (!remaining.contains(dep)) {
                    continue;
                }
                if (!visited.contains(dep)) {
                    visited.add(dep);
                    parent.put(dep, current);
                    stack.push(dep);
                    expanded = true;
                    break;
                } else if (stack.contains(dep)) {
                    // Found the cycle — dep is already on the stack
                    parent.put(dep, current);
                    cycleEnd = dep;
                    break outer;
                }
            }

            if (!expanded) {
                stack.pop();
            }
        }

        if (cycleEnd == null) {
            // Fallback: just list remaining nodes
            List<String> fallback = new ArrayList<>();
            String first = remaining.iterator().next().getSimpleName();
            for (Class<?> node : remaining) {
                fallback.add(node.getSimpleName());
            }
            fallback.add(first);
            return fallback;
        }

        // Reconstruct cycle path from parent map
        List<String> cyclePath = new ArrayList<>();
        Class<?> current = parent.get(cycleEnd);
        cyclePath.add(cycleEnd.getSimpleName());

        while (current != null && !current.equals(cycleEnd)) {
            cyclePath.add(current.getSimpleName());
            current = parent.get(current);
        }
        cyclePath.add(cycleEnd.getSimpleName());
        Collections.reverse(cyclePath);

        return cyclePath;
    }

    private void undoAdd(Class<?> moduleType, Set<Class<?>> deps) {
        dependencies.remove(moduleType);
        for (Class<?> dep : deps) {
            Set<Class<?>> depDependents = dependents.get(dep);
            if (depDependents != null) {
                depDependents.remove(moduleType);
                if (depDependents.isEmpty()) {
                    dependents.remove(dep);
                }
            }
        }
        dependents.remove(moduleType);
    }
}
