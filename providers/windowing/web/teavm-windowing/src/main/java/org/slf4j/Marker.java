package org.slf4j;

import java.util.Iterator;

/**
 * Minimal SLF4J Marker shim for TeaVM.
 * TeaVM cannot compile the real SLF4J because it uses SecurityManager,
 * LinkedBlockingQueue, and ClassLoader.getResources().
 */
public interface Marker {
    String getName();
    void add(Marker reference);
    boolean remove(Marker reference);
    boolean hasChildren();
    boolean hasReferences();
    Iterator<Marker> iterator();
    boolean contains(Marker other);
    boolean contains(String name);
}
