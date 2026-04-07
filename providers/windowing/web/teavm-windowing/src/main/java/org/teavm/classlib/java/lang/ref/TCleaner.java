package org.teavm.classlib.java.lang.ref;

/**
 * TeaVM shim for java.lang.ref.Cleaner.
 * In the browser there is no GC-based destructor mechanism, so register()
 * returns a Cleanable that can be manually cleaned but will never auto-trigger.
 */
public final class TCleaner {

    public interface Cleanable {
        void clean();
    }

    private TCleaner() {}

    public static TCleaner create() {
        return new TCleaner();
    }

    public Cleanable register(Object obj, Runnable action) {
        return new CleanableImpl(action);
    }

    private static final class CleanableImpl implements Cleanable {
        private Runnable action;

        CleanableImpl(Runnable action) {
            this.action = action;
        }

        @Override
        public void clean() {
            var a = action;
            action = null;
            if (a != null) {
                a.run();
            }
        }
    }
}
