package org.teavm.classlib.java.util.concurrent;

import java.util.ArrayDeque;
import java.util.Collection;
import org.teavm.classlib.java.util.TAbstractQueue;
import org.teavm.classlib.java.util.TIterator;

/**
 * TeaVM shim for ConcurrentLinkedQueue.
 * Browser is single-threaded, so a plain ArrayDeque is sufficient.
 */
public class TConcurrentLinkedQueue<E> extends TAbstractQueue<E> {

    private final ArrayDeque<E> deque = new ArrayDeque<>();

    public TConcurrentLinkedQueue() {}

    public TConcurrentLinkedQueue(Collection<? extends E> c) {
        deque.addAll(c);
    }

    @Override public boolean offer(E e) { return deque.offer(e); }
    @Override public E poll() { return deque.poll(); }
    @Override public E peek() { return deque.peek(); }
    @Override public int size() { return deque.size(); }
    @Override public boolean isEmpty() { return deque.isEmpty(); }
    @Override public boolean contains(Object o) { return deque.contains(o); }

    @Override
    public TIterator<E> iterator() {
        var it = deque.iterator();
        return new TIterator<>() {
            @Override public boolean hasNext() { return it.hasNext(); }
            @Override public E next() { return it.next(); }
            @Override public void remove() { it.remove(); }
        };
    }

    @Override public Object[] toArray() { return deque.toArray(); }
    @Override public <T> T[] toArray(T[] a) { return deque.toArray(a); }
    @Override public boolean remove(Object o) { return deque.remove(o); }
    @Override public void clear() { deque.clear(); }
}
