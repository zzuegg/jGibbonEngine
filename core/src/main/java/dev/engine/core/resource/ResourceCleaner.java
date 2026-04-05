package dev.engine.core.resource;

import java.lang.ref.Cleaner;

public final class ResourceCleaner {

    private static final Cleaner CLEANER = Cleaner.create();

    private ResourceCleaner() {}

    public static Cleaner.Cleanable register(Object resource, Runnable cleanupAction) {
        return CLEANER.register(resource, cleanupAction);
    }
}
