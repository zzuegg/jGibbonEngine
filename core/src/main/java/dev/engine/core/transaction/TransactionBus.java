package dev.engine.core.transaction;

import dev.engine.core.scene.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Multi-consumer transaction bus with per-subscriber filtering and double-buffered swap.
 *
 * <p>Logic thread emits transactions via {@link #emit(Transaction)}.
 * Each subscriber registers interest in specific {@link Component} types.
 * On {@link #drain(Object)}, the subscriber's buffer is swapped and returned —
 * one lightweight lock per drain, zero contention on emit within the same thread.
 *
 * <p>For {@link Transaction.ComponentChanged} transactions, the bus filters by the
 * component's {@link Component#slotType()} against the subscriber's registered types.
 * All other transaction types (e.g., {@link Transaction.EntityAdded}) are delivered
 * to all subscribers.
 */
public class TransactionBus {

    private final Map<Object, SubscriberState> subscribers = new HashMap<>();

    /**
     * Registers a subscriber with interest in the given component types.
     * ComponentChanged transactions are filtered by these types.
     * EntityAdded/EntityRemoved are always delivered.
     */
    public void subscribe(Object subscriberKey, Class<? extends Component>... componentTypes) {
        var types = new HashSet<Class<? extends Component>>();
        for (var t : componentTypes) types.add(t);
        subscribers.put(subscriberKey, new SubscriberState(types));
    }

    /**
     * Emits a transaction. For ComponentChanged, only subscribers interested
     * in that component's slotType receive it. All other types go to all subscribers.
     */
    public void emit(Transaction txn) {
        if (txn instanceof Transaction.ComponentChanged cc) {
            var slotType = cc.component().slotType();
            for (var state : subscribers.values()) {
                if (state.componentTypes.contains(slotType)) {
                    state.writeBuffer.add(txn);
                }
            }
        } else {
            // Lifecycle and other transactions go to all subscribers
            for (var state : subscribers.values()) {
                state.writeBuffer.add(txn);
            }
        }
    }

    /**
     * Drains all pending transactions for the given subscriber.
     * Swaps the double buffer under a lock, returns the read buffer.
     * The returned list is safe to iterate while the logic thread continues emitting.
     */
    public List<Transaction> drain(Object subscriberKey) {
        var state = subscribers.get(subscriberKey);
        if (state == null) return List.of();
        return state.swap();
    }

    private static class SubscriberState {
        final Set<Class<? extends Component>> componentTypes;
        List<Transaction> writeBuffer = new ArrayList<>();
        List<Transaction> readBuffer = new ArrayList<>();

        SubscriberState(Set<Class<? extends Component>> componentTypes) {
            this.componentTypes = componentTypes;
        }

        synchronized List<Transaction> swap() {
            var result = writeBuffer;
            writeBuffer = readBuffer;
            writeBuffer.clear();
            readBuffer = result;
            return result;
        }
    }
}
