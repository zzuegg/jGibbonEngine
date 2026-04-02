package dev.engine.core.handle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class HandlePool {

    private final List<Integer> generations = new ArrayList<>();
    private final Deque<Integer> freeIndices = new ArrayDeque<>();
    private final Object lock = new Object();

    public Handle allocate() {
        synchronized (lock) {
            if (!freeIndices.isEmpty()) {
                int index = freeIndices.poll();
                int gen = generations.get(index);
                return new Handle(index, gen);
            }
            int index = generations.size();
            generations.add(0);
            return new Handle(index, 0);
        }
    }

    public void release(Handle handle) {
        synchronized (lock) {
            if (handle.index() < 0 || handle.index() >= generations.size()) return;
            if (generations.get(handle.index()) != handle.generation()) return;
            generations.set(handle.index(), handle.generation() + 1);
            freeIndices.add(handle.index());
        }
    }

    public boolean isValid(Handle handle) {
        synchronized (lock) {
            if (handle.index() < 0 || handle.index() >= generations.size()) return false;
            return generations.get(handle.index()) == handle.generation();
        }
    }
}
