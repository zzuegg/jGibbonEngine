package org.teavm.classlib.java.util.concurrent;

/**
 * TeaVM shim for CountDownLatch.
 * Browser is single-threaded — the executor runs tasks inline,
 * so count reaches zero before await() is called.
 */
public class TCountDownLatch {

    private int count;

    public TCountDownLatch(int count) {
        if (count < 0) throw new IllegalArgumentException("count < 0");
        this.count = count;
    }

    public void await() throws InterruptedException {
        // Single-threaded: tasks run inline, count should already be 0.
    }

    public boolean await(long timeout, TTimeUnit unit) throws InterruptedException {
        return count == 0;
    }

    public void countDown() {
        if (count > 0) count--;
    }

    public long getCount() {
        return count;
    }
}
