package dev.engine.graphics.renderer;

import dev.engine.core.handle.Handle;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.transaction.Transaction;
import dev.engine.core.property.PropertyMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaterialTransactionTest {

    static final PropertyKey<MaterialData, Vec3> ALBEDO = PropertyKey.of("albedoColor", Vec3.class);
    static final PropertyKey<MaterialData, Float> ROUGHNESS = PropertyKey.of("roughness", Float.class);

    private MeshRenderer renderer;

    @BeforeEach
    void setUp() { renderer = new MeshRenderer(); }

    @Test void materialPropertyChangedUpdatesRenderer() {
        var entity = new Handle<>(0, 0);
        renderer.processTransaction(new Transaction.EntityAdded(entity));
        renderer.processTransaction(new Transaction.MaterialPropertyChanged(entity, ALBEDO, new Vec3(1, 0, 0)));

        var mat = renderer.getMaterial(entity);
        assertNotNull(mat);
        assertEquals(new Vec3(1, 0, 0), mat.get(ALBEDO));
    }

    @Test void materialReplacedSetsAllProperties() {
        var entity = new Handle<>(0, 0);
        renderer.processTransaction(new Transaction.EntityAdded(entity));

        var props = PropertyMap.<MaterialData>builder()
                .set(ALBEDO, new Vec3(0, 1, 0))
                .set(ROUGHNESS, 0.5f)
                .build();
        renderer.processTransaction(new Transaction.MaterialReplaced(entity, props));

        var mat = renderer.getMaterial(entity);
        assertEquals(new Vec3(0, 1, 0), mat.get(ALBEDO));
        assertEquals(0.5f, mat.get(ROUGHNESS));
    }

    @Test void materialDataIncludedInDrawCommand() {
        var entity = new Handle<>(0, 0);
        renderer.processTransaction(new Transaction.EntityAdded(entity));
        renderer.processTransaction(new Transaction.MaterialPropertyChanged(entity, ROUGHNESS, 0.8f));

        var vbo = new Handle<dev.engine.graphics.BufferResource>(0, 0);
        var vi = new Handle<dev.engine.graphics.VertexInputResource>(0, 0);
        var pipe = new Handle<dev.engine.graphics.PipelineResource>(0, 0);
        renderer.setRenderable(entity, new Renderable(vbo, null, vi, pipe, 3, 0));

        var batch = renderer.collectBatch();
        assertEquals(1, batch.size());
        var cmd = batch.getFirst();
        assertNotNull(cmd.materialData());
        assertEquals(0.8f, cmd.materialData().get(ROUGHNESS));
    }

    @Test void entityRemovalClearsMaterial() {
        var entity = new Handle<>(0, 0);
        renderer.processTransaction(new Transaction.EntityAdded(entity));
        renderer.processTransaction(new Transaction.MaterialPropertyChanged(entity, ROUGHNESS, 0.5f));
        renderer.processTransaction(new Transaction.EntityRemoved(entity));
        assertNull(renderer.getMaterial(entity));
    }
}
