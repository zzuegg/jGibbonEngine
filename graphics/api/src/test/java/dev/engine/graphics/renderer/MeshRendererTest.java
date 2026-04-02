package dev.engine.graphics.renderer;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import dev.engine.core.transaction.Transaction;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.VertexInputResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MeshRendererTest {

    private MeshRenderer renderer;
    private List<DrawCommand> drawCommands;

    @BeforeEach
    void setUp() {
        drawCommands = new ArrayList<>();
        renderer = new MeshRenderer();
    }

    @Nested
    class EntityTracking {
        @Test void addedEntityIsTracked() {
            var entity = new Handle<>(0, 0);
            renderer.processTransaction(new Transaction.EntityAdded(entity));
            assertTrue(renderer.hasEntity(entity));
        }

        @Test void removedEntityIsUntracked() {
            var entity = new Handle<>(0, 0);
            renderer.processTransaction(new Transaction.EntityAdded(entity));
            renderer.processTransaction(new Transaction.EntityRemoved(entity));
            assertFalse(renderer.hasEntity(entity));
        }

        @Test void transformUpdateStored() {
            var entity = new Handle<>(0, 0);
            var transform = Mat4.translation(1f, 2f, 3f);
            renderer.processTransaction(new Transaction.EntityAdded(entity));
            renderer.processTransaction(new Transaction.TransformChanged(entity, transform));
            assertEquals(transform, renderer.getTransform(entity));
        }
    }

    @Nested
    class Renderables {
        @Test void assignMeshToEntity() {
            var entity = new Handle<>(0, 0);
            renderer.processTransaction(new Transaction.EntityAdded(entity));

            var vbo = new Handle<BufferResource>(0, 0);
            var vertexInput = new Handle<VertexInputResource>(0, 0);
            var pipeline = new Handle<PipelineResource>(0, 0);
            renderer.setRenderable(entity, new Renderable(vbo, null, vertexInput, pipeline, 36, 0));

            var renderable = renderer.getRenderable(entity);
            assertNotNull(renderable);
            assertEquals(36, renderable.vertexCount());
        }
    }

    @Nested
    class BatchCollection {
        @Test void collectDrawBatch() {
            var e1 = new Handle<>(0, 0);
            var e2 = new Handle<>(1, 0);
            renderer.processTransaction(new Transaction.EntityAdded(e1));
            renderer.processTransaction(new Transaction.EntityAdded(e2));

            var vbo = new Handle<BufferResource>(0, 0);
            var vi = new Handle<VertexInputResource>(0, 0);
            var pipe = new Handle<PipelineResource>(0, 0);
            renderer.setRenderable(e1, new Renderable(vbo, null, vi, pipe, 3, 0));
            renderer.setRenderable(e2, new Renderable(vbo, null, vi, pipe, 6, 0));

            var batch = renderer.collectBatch();
            assertEquals(2, batch.size());
        }
    }
}
