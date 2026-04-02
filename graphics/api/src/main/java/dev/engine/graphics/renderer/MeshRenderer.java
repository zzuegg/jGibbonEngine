package dev.engine.graphics.renderer;

import dev.engine.core.handle.Handle;
import dev.engine.core.math.Mat4;
import dev.engine.core.property.MutablePropertyMap;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.property.PropertyMap;
import dev.engine.core.transaction.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Processes scene transactions and maintains the renderer's internal
 * representation of entities, transforms, materials, and renderables.
 *
 * <p>Completely independent of the scene — only consumes transactions.
 * Material changes flow through transactions, not by reading Material objects directly.
 */
public class MeshRenderer {

    private final Map<Handle<?>, Mat4> transforms = new HashMap<>();
    private final Map<Handle<?>, Renderable> renderables = new HashMap<>();
    private final Map<Handle<?>, MutablePropertyMap> materials = new HashMap<>();

    public void processTransaction(Transaction txn) {
        switch (txn) {
            case Transaction.EntityAdded added -> {
                transforms.put(added.entity(), Mat4.IDENTITY);
                materials.put(added.entity(), new MutablePropertyMap());
            }
            case Transaction.EntityRemoved removed -> {
                transforms.remove(removed.entity());
                renderables.remove(removed.entity());
                materials.remove(removed.entity());
            }
            case Transaction.TransformChanged changed ->
                    transforms.put(changed.entity(), changed.transform());
            case Transaction.MaterialPropertyChanged changed -> {
                var mat = materials.get(changed.entity());
                if (mat != null) {
                    @SuppressWarnings("unchecked")
                    var key = (PropertyKey<Object>) changed.key();
                    mat.set(key, changed.value());
                }
            }
            case Transaction.MaterialReplaced replaced -> {
                var mat = materials.get(replaced.entity());
                if (mat != null && replaced.material() != null) {
                    // Replace all properties from the snapshot
                    for (var key : replaced.material().keys()) {
                        @SuppressWarnings("unchecked")
                        var typedKey = (PropertyKey<Object>) key;
                        mat.set(typedKey, replaced.material().get(key));
                    }
                }
            }
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

    public MutablePropertyMap getMaterial(Handle<?> entity) {
        return materials.get(entity);
    }

    public List<DrawCommand> collectBatch() {
        var batch = new ArrayList<DrawCommand>();
        for (var entry : renderables.entrySet()) {
            var entity = entry.getKey();
            var transform = transforms.getOrDefault(entity, Mat4.IDENTITY);
            var mat = materials.get(entity);
            var snapshot = mat != null ? mat.snapshot() : PropertyMap.builder().build();
            batch.add(new DrawCommand(entity, entry.getValue(), transform, snapshot));
        }
        return batch;
    }
}
