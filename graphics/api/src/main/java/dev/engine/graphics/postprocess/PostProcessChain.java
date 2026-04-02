package dev.engine.graphics.postprocess;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class PostProcessChain {

    private final List<String> order = new ArrayList<>();
    private final Map<String, PostProcessEffect> effects = new LinkedHashMap<>();
    private final Set<String> disabled = new HashSet<>();

    public void add(String name, PostProcessEffect effect) {
        order.add(name);
        effects.put(name, effect);
    }

    public void remove(String name) {
        order.remove(name);
        effects.remove(name);
        disabled.remove(name);
    }

    public void setEnabled(String name, boolean enabled) {
        if (enabled) disabled.remove(name);
        else disabled.add(name);
    }

    public int size() { return order.size(); }

    public void execute(PostProcessContext context) {
        for (var name : order) {
            if (!disabled.contains(name)) {
                effects.get(name).apply(context);
            }
        }
    }
}
