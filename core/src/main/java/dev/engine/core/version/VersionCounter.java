package dev.engine.core.version;

import java.util.concurrent.atomic.AtomicLong;

public class VersionCounter {

    private final AtomicLong version = new AtomicLong(0);

    public long version() {
        return version.get();
    }

    public void increment() {
        version.incrementAndGet();
    }

    public boolean hasChangedSince(long snapshot) {
        return version.get() != snapshot;
    }
}
