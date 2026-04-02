package dev.engine.core.rendergraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RenderGraph {

    private final Map<String, RenderPass> passes = new LinkedHashMap<>();
    private List<String> executionOrder = new ArrayList<>();

    public void addPass(String name, RenderPass pass) {
        passes.put(name, pass);
    }

    public void removePass(String name) {
        passes.remove(name);
    }

    public void compile() {
        // Collect resource dependencies
        Map<String, PassBuilder> builders = new LinkedHashMap<>();
        for (var entry : passes.entrySet()) {
            var builder = new PassBuilder();
            entry.getValue().setup(builder);
            builders.put(entry.getKey(), builder);
        }

        // Build dependency graph: which pass must run before which
        // resource -> pass that writes it
        Map<String, String> resourceProducers = new HashMap<>();
        for (var entry : builders.entrySet()) {
            for (var resource : entry.getValue().getWrites()) {
                resourceProducers.put(resource, entry.getKey());
            }
        }

        // Build adjacency: pass -> set of passes it depends on
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (var passName : passes.keySet()) {
            dependencies.put(passName, new LinkedHashSet<>());
        }
        for (var entry : builders.entrySet()) {
            for (var resource : entry.getValue().getReads()) {
                var producer = resourceProducers.get(resource);
                if (producer != null && !producer.equals(entry.getKey())) {
                    dependencies.get(entry.getKey()).add(producer);
                }
            }
        }

        // Topological sort (Kahn's algorithm)
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Set<String>> dependents = new HashMap<>();
        for (var passName : passes.keySet()) {
            inDegree.put(passName, 0);
            dependents.put(passName, new LinkedHashSet<>());
        }
        for (var entry : dependencies.entrySet()) {
            inDegree.put(entry.getKey(), entry.getValue().size());
            for (var dep : entry.getValue()) {
                dependents.get(dep).add(entry.getKey());
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) queue.add(entry.getKey());
        }

        executionOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            var node = queue.poll();
            executionOrder.add(node);
            for (var dep : dependents.get(node)) {
                int newDeg = inDegree.get(dep) - 1;
                inDegree.put(dep, newDeg);
                if (newDeg == 0) queue.add(dep);
            }
        }

        if (executionOrder.size() != passes.size()) {
            throw new IllegalStateException("Render graph has cyclic dependencies");
        }
    }

    public void execute() {
        for (var name : executionOrder) {
            var pass = passes.get(name);
            if (pass != null) {
                pass.execute(new PassContext(name));
            }
        }
    }

    public List<String> getExecutionOrder() {
        return List.copyOf(executionOrder);
    }
}
