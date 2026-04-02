package dev.engine.core.frame;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class DoubleBufferedPipeline {

    private final BlockingQueue<FrameSnapshot> queue = new ArrayBlockingQueue<>(1);

    public void publishSnapshot(FrameSnapshot snapshot) {
        // Drop the old snapshot if not yet consumed, replace with new
        queue.poll();
        queue.offer(snapshot);
    }

    public FrameSnapshot consumeSnapshot() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
