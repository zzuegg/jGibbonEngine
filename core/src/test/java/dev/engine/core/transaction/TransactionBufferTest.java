package dev.engine.core.transaction;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionBufferTest {

    private TransactionBuffer buffer;

    @BeforeEach
    void setUp() { buffer = new TransactionBuffer(); }

    @Nested
    class Recording {
        @Test void emptyBufferHasNoTransactions() {
            assertTrue(buffer.drain().isEmpty());
        }

        @Test void recordEntityAdded() {
            var entity = new Handle(0, 0);
            buffer.added(entity);
            var txns = buffer.drain();
            assertEquals(1, txns.size());
            assertInstanceOf(Transaction.EntityAdded.class, txns.getFirst());
            assertEquals(entity, ((Transaction.EntityAdded) txns.getFirst()).entity());
        }

        @Test void recordEntityRemoved() {
            var entity = new Handle(1, 0);
            buffer.removed(entity);
            var txns = buffer.drain();
            assertEquals(1, txns.size());
            assertInstanceOf(Transaction.EntityRemoved.class, txns.getFirst());
        }

        @Test void recordTransformChanged() {
            var entity = new Handle(2, 0);
            var transform = Mat4.translation(1f, 2f, 3f);
            buffer.transformChanged(entity, transform);
            var txns = buffer.drain();
            assertEquals(1, txns.size());
            var txn = (Transaction.TransformChanged) txns.getFirst();
            assertEquals(entity, txn.entity());
            assertEquals(transform, txn.transform());
        }

        @Test void recordMaterialPropertyChanged() {
            var entity = new Handle(3, 0);
            var key = PropertyKey.of("roughness", Float.class);
            buffer.materialPropertyChanged(entity, key, 0.8f);
            var txns = buffer.drain();
            assertEquals(1, txns.size());
            var txn = (Transaction.MaterialPropertyChanged) txns.getFirst();
            assertEquals(entity, txn.entity());
            assertEquals(key, txn.key());
            assertEquals(0.8f, txn.value());
        }

        @Test void multipleTransactionsPreserveOrder() {
            var e1 = new Handle(0, 0);
            var e2 = new Handle(1, 0);
            buffer.added(e1);
            buffer.transformChanged(e1, Mat4.IDENTITY);
            buffer.added(e2);
            var txns = buffer.drain();
            assertEquals(3, txns.size());
            assertInstanceOf(Transaction.EntityAdded.class, txns.get(0));
            assertInstanceOf(Transaction.TransformChanged.class, txns.get(1));
            assertInstanceOf(Transaction.EntityAdded.class, txns.get(2));
        }
    }

    @Nested
    class Draining {
        @Test void drainClearsBuffer() {
            buffer.added(new Handle(0, 0));
            buffer.drain();
            assertTrue(buffer.drain().isEmpty());
        }

        @Test void drainReturnsUnmodifiableList() {
            buffer.added(new Handle(0, 0));
            var txns = buffer.drain();
            assertThrows(UnsupportedOperationException.class, () -> txns.add(null));
        }
    }
}
