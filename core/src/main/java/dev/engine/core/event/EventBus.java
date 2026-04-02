package dev.engine.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <E extends Event> Subscription subscribe(Class<E> eventType, Consumer<E> listener) {
        var list = listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>());
        list.add(listener);
        return new Subscription(() -> list.remove(listener));
    }

    @SuppressWarnings("unchecked")
    public <E extends Event> void publish(E event) {
        var list = listeners.get(event.getClass());
        if (list == null) return;
        for (var listener : list) {
            try {
                ((Consumer<E>) listener).accept(event);
            } catch (Exception e) {
                log.warn("Event listener threw exception for {}: {}",
                        event.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
    }

    public static class Subscription {
        private final Runnable unsubscribeAction;
        private volatile boolean active = true;

        Subscription(Runnable unsubscribeAction) {
            this.unsubscribeAction = unsubscribeAction;
        }

        public void unsubscribe() {
            if (active) {
                active = false;
                unsubscribeAction.run();
            }
        }
    }
}
