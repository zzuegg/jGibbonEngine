package dev.engine.graphics.renderer;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import dev.engine.core.transaction.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes scene transactions and maintains the renderer's internal
 * representation of entities, transforms, and renderables.
 *
 * <p>Completely independent of the scene — only consumes transactions.
 */
public class MeshRenderer {

    private final Map<Handle<?>, Mat4> transforms = new HashMap<>();
    private final Map<Handle<?>, Renderable> renderables = new HashMap<>();

    public void processTransaction(Transaction txn) {
        switch (txn) {
            case Transaction.EntityAdded added ->
                    transforms.put(added.entity(), Mat4.IDENTITY);
            case Transaction.EntityRemoved removed -> {
                transforms.remove(removed.entity());
                renderables.remove(removed.entity());
            }
            case Transaction.TransformChanged changed ->
                    transforms.put(changed.entity(), changed.transform());
            case Transaction.MaterialPropertyChanged ignored -> {}
            case Transaction.MaterialReplaced ignored -> {}
            case Transaction.MeshChanged ignored -> {}
        }
    }

    public void processTransactions(List<Transaction> txns) {
        for (var txn : txns) processTransaction(txn);
    }

    public boolean hasEntity(Handle<?> entity) {
        return transforms.containsKey(entity);
    }

    public Mat4 getTransform(Handle<?> entity) {
        return transforms.get(entity);
    }

    public void setRenderable(Handle<?> entity, Renderable renderable) {
        renderables.put(entity, renderable);
    }

    public Renderable getRenderable(Handle<?> entity) {
        return renderables.get(entity);
    }

    public List<DrawCommand> collectBatch() {
        var batch = new ArrayList<DrawCommand>();
        for (var entry : renderables.entrySet()) {
            var transform = transforms.getOrDefault(entry.getKey(), Mat4.IDENTITY);
            batch.add(new DrawCommand(entry.getValue(), transform));
        }
        return batch;
    }
}
