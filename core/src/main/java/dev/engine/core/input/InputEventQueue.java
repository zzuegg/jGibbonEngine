package dev.engine.core.input;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

/**
 * Thread-safe event queue. Providers push events from any thread;
 * the game loop drains them at the start of each frame.
 *
 * <p>Uses synchronized ArrayDeque instead of ConcurrentLinkedQueue
 * for TeaVM compatibility (synchronized is a no-op in the browser).
 */
public class InputEventQueue {

    private final Queue<InputEvent> inputEvents = new ArrayDeque<>();
    private final Queue<WindowEvent> windowEvents = new ArrayDeque<>();

    public synchronized void pushInput(InputEvent event) {
        inputEvents.add(event);
    }

    public synchronized void pushWindow(WindowEvent event) {
        windowEvents.add(event);
    }

    public synchronized List<InputEvent> drainInput() {
        return drain(inputEvents);
    }

    public synchronized List<WindowEvent> drainWindow() {
        return drain(windowEvents);
    }

    private static <E> List<E> drain(Queue<E> queue) {
        var result = new ArrayList<E>(queue.size());
        E event;
        while ((event = queue.poll()) != null) {
            result.add(event);
        }
        return Collections.unmodifiableList(result);
    }
}
