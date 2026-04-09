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
 * one lightweight lock per subscriber, minimal contention between emit and drain.
 *
 * <p>For {@link Transaction.ComponentChanged} transactions, the bus filters by the
 * component's {@link Component#slotType()} against the subscriber's registered types.
 * All other transaction types (e.g., {@link Transaction.EntityAdded}) are delivered
 * to all subscribers.
 *
 * <p>Thread safety: emit() and drain() are synchronized per-subscriber via
 * the SubscriberState lock. This ensures the logic thread (emit) and render
 * thread (drain/swap) never access the write buffer concurrently.
 */
public class TransactionBus {

    private final Map<Object, SubscriberState> subscribers = new HashMap<>();

    /**
     * Registers a subscriber with interest in the given component types.
     * ComponentChanged transactions are filtered by these types.
     * EntityAdded/EntityRemoved and all other types are always delivered.
     * If no component types are specified, all transactions are delivered.
     */
    @SafeVarargs
    public final void subscribe(Object subscriberKey, Class<? extends Component>... componentTypes) {
        var types = new HashSet<Class<? extends Component>>();
        for (var t : componentTypes) types.add(t);
        subscribers.put(subscriberKey, new SubscriberState(types));
    }

    /**
     * Removes a subscriber, freeing its buffers.
     */
    public void unsubscribe(Object subscriberKey) {
        subscribers.remove(subscriberKey);
    }

    /**
     * Emits a transaction. For ComponentChanged, only subscribers interested
     * in that component's slotType receive it (unless the subscriber has no filter).
     * All other types go to all subscribers.
     */
    public void emit(Transaction txn) {
        if (txn instanceof Transaction.ComponentChanged cc) {
            var slotType = cc.component().slotType();
            for (var state : subscribers.values()) {
                if (state.componentTypes.isEmpty() || state.componentTypes.contains(slotType)) {
                    state.add(txn);
                }
            }
        } else {
            // Lifecycle and other transactions go to all subscribers
            for (var state : subscribers.values()) {
                state.add(txn);
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

        SubscriberState(Set<Class<? extends Component>> componentTypes) {
            this.componentTypes = componentTypes;
        }

        synchronized void add(Transaction txn) {
            writeBuffer.add(txn);
        }

        synchronized List<Transaction> swap() {
            var snapshot = writeBuffer;
            writeBuffer = new ArrayList<>();
            return snapshot;
        }
    }
}
