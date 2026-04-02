package dev.engine.core.frame;

import dev.engine.core.transaction.Transaction;
import dev.engine.core.transaction.TransactionBuffer;
import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FramePipelineTest {

    @Nested
    class FrameSnapshotTests {
        @Test void snapshotCapturesTransactions() {
            var buffer = new TransactionBuffer();
            buffer.added(new Handle(0, 0));
            buffer.transformChanged(new Handle(0, 0), Mat4.IDENTITY);

            var snapshot = new FrameSnapshot(1, buffer.drain());
            assertEquals(1, snapshot.frameNumber());
            assertEquals(2, snapshot.transactions().size());
        }

        @Test void snapshotTransactionsAreImmutable() {
            var snapshot = new FrameSnapshot(1, List.of());
            assertThrows(UnsupportedOperationException.class,
                    () -> snapshot.transactions().add(null));
        }
    }

    @Nested
    class DoubleBufferSwap {
        @Test void producerAndConsumerExchangeSnapshots() throws InterruptedException {
            var pipeline = new DoubleBufferedPipeline();
            var consumed = new AtomicReference<FrameSnapshot>();
            var latch = new CountDownLatch(1);

            // Consumer thread
            Thread consumer = Thread.ofVirtual().start(() -> {
                consumed.set(pipeline.consumeSnapshot());
                latch.countDown();
            });

            // Producer publishes
            var snapshot = new FrameSnapshot(42, List.of(
                    new Transaction.EntityAdded(new Handle(0, 0))
            ));
            pipeline.publishSnapshot(snapshot);

            assertTrue(latch.await(2, TimeUnit.SECONDS));
            assertNotNull(consumed.get());
            assertEquals(42, consumed.get().frameNumber());
            assertEquals(1, consumed.get().transactions().size());
        }

        @Test void consumerGetsLatestSnapshot() throws InterruptedException {
            var pipeline = new DoubleBufferedPipeline();

            pipeline.publishSnapshot(new FrameSnapshot(1, List.of()));
            pipeline.publishSnapshot(new FrameSnapshot(2, List.of()));

            var snap = pipeline.consumeSnapshot();
            assertEquals(2, snap.frameNumber());
        }

        @Test void producerDoesNotBlockWhenConsumerIsSlow() throws InterruptedException {
            var pipeline = new DoubleBufferedPipeline();

            // Publish several frames without consuming — should not block
            for (int i = 0; i < 10; i++) {
                pipeline.publishSnapshot(new FrameSnapshot(i + 1, List.of()));
            }

            // Consumer gets the latest
            var snap = pipeline.consumeSnapshot();
            assertNotNull(snap);
            assertTrue(snap.frameNumber() >= 1);
        }
    }
}
