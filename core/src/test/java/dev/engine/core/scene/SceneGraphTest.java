package dev.engine.core.scene;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.transaction.Transaction;
import org.junit.jupiter.api.BeforeEach;
import static dev.engine.core.scene.SceneAccess.drainTransactions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class SceneGraphTest {

    private Scene scene;

    @BeforeEach
    void setUp() { scene = new Scene(); }

    @Nested
    class EntityLifecycle {
        @Test void createEntityReturnsUniqueId() {
            var a = scene.createEntity();
            var b = scene.createEntity();
            assertNotEquals(a, b);
        }

        @Test void createEntityEmitsAddedTransaction() {
            var entity = scene.createEntity();
            var txns = drainTransactions(scene);
            assertEquals(1, txns.size());
            assertInstanceOf(Transaction.EntityAdded.class, txns.getFirst());
        }

        @Test void destroyEntityEmitsRemovedTransaction() {
            var entity = scene.createEntity();
            drainTransactions(scene); // clear
            scene.destroyEntity(entity);
            var txns = drainTransactions(scene);
            assertEquals(1, txns.size());
            assertInstanceOf(Transaction.EntityRemoved.class, txns.getFirst());
        }
    }

    @Nested
    class TransformHierarchy {
        @Test void setLocalTransform() {
            var entity = scene.createEntity();
            var t = Mat4.translation(1f, 2f, 3f);
            scene.setLocalTransform(entity, t);
            assertEquals(t, scene.getLocalTransform(entity));
        }

        @Test void worldTransformWithoutParentEqualsLocal() {
            var entity = scene.createEntity();
            var t = Mat4.translation(5f, 0f, 0f);
            scene.setLocalTransform(entity, t);
            assertEquals(t, scene.getWorldTransform(entity));
        }

        @Test void childWorldTransformIncludesParent() {
            var parent = scene.createEntity();
            var child = scene.createEntity();
            scene.setParent(child, parent);
            scene.setLocalTransform(parent, Mat4.translation(10f, 0f, 0f));
            scene.setLocalTransform(child, Mat4.translation(0f, 5f, 0f));

            var world = scene.getWorldTransform(child);
            // Parent translates +10x, child translates +5y
            // World should be translation(10, 5, 0)
            var point = world.transform(new dev.engine.core.math.Vec4(0f, 0f, 0f, 1f));
            assertEquals(10f, point.x(), 1e-5f);
            assertEquals(5f, point.y(), 1e-5f);
        }

        @Test void settingTransformEmitsTransaction() {
            var entity = scene.createEntity();
            drainTransactions(scene);
            scene.setLocalTransform(entity, Mat4.translation(1f, 0f, 0f));
            var txns = drainTransactions(scene);
            assertTrue(txns.stream().anyMatch(t -> t instanceof Transaction.ComponentChanged cc
                    && cc.component() instanceof dev.engine.core.scene.component.Transform));
        }

        @Test void removeParent() {
            var parent = scene.createEntity();
            var child = scene.createEntity();
            scene.setParent(child, parent);
            scene.setLocalTransform(parent, Mat4.translation(10f, 0f, 0f));
            scene.setLocalTransform(child, Mat4.translation(0f, 5f, 0f));

            scene.removeParent(child);
            var world = scene.getWorldTransform(child);
            var point = world.transform(new dev.engine.core.math.Vec4(0f, 0f, 0f, 1f));
            assertEquals(0f, point.x(), 1e-5f);
            assertEquals(5f, point.y(), 1e-5f);
        }
    }

    @Nested
    class Children {
        @Test void parentTracksChildren() {
            var parent = scene.createEntity();
            var c1 = scene.createEntity();
            var c2 = scene.createEntity();
            scene.setParent(c1, parent);
            scene.setParent(c2, parent);
            var children = scene.getChildren(parent);
            assertEquals(2, children.size());
            assertTrue(children.contains(c1));
            assertTrue(children.contains(c2));
        }

        @Test void destroyParentDestroysChildren() {
            var parent = scene.createEntity();
            var child = scene.createEntity();
            scene.setParent(child, parent);
            drainTransactions(scene);

            scene.destroyEntity(parent);
            var txns = drainTransactions(scene);
            // Should have removed both parent and child
            long removals = txns.stream()
                    .filter(t -> t instanceof Transaction.EntityRemoved)
                    .count();
            assertEquals(2, removals);
        }
    }

    @Nested
    class HierarchyTransactions {
        @Test void setParentEmitsHierarchyTransactions() {
            var parent = scene.createEntity();
            var child = scene.createEntity();
            drainTransactions(scene); // clear entity-added transactions

            child.setParent(parent);
            var txns = drainTransactions(scene);

            // Should emit ComponentChanged for Hierarchy on both child and parent
            var hierarchyChanges = txns.stream()
                    .filter(t -> t instanceof Transaction.ComponentChanged cc
                            && cc.component() instanceof dev.engine.core.scene.component.Hierarchy)
                    .toList();
            assertTrue(hierarchyChanges.size() >= 2,
                    "Expected at least 2 Hierarchy transactions (child + parent), got " + hierarchyChanges.size());
        }

        @Test void removeParentEmitsHierarchyTransactions() {
            var parent = scene.createEntity();
            var child = scene.createEntity();
            child.setParent(parent);
            drainTransactions(scene); // clear

            scene.removeParent(child);
            var txns = drainTransactions(scene);

            var hierarchyChanges = txns.stream()
                    .filter(t -> t instanceof Transaction.ComponentChanged cc
                            && cc.component() instanceof dev.engine.core.scene.component.Hierarchy)
                    .toList();
            assertTrue(hierarchyChanges.size() >= 2,
                    "Expected at least 2 Hierarchy transactions (child + parent), got " + hierarchyChanges.size());
        }

        @Test void reparentEmitsTransactionsForOldAndNewParent() {
            var parent1 = scene.createEntity();
            var parent2 = scene.createEntity();
            var child = scene.createEntity();
            child.setParent(parent1);
            drainTransactions(scene); // clear

            child.setParent(parent2);
            var txns = drainTransactions(scene);

            // Should emit for: old parent (removeChild), child (setParent), new parent (addChild)
            var hierarchyChanges = txns.stream()
                    .filter(t -> t instanceof Transaction.ComponentChanged cc
                            && cc.component() instanceof dev.engine.core.scene.component.Hierarchy)
                    .toList();
            assertTrue(hierarchyChanges.size() >= 3,
                    "Expected at least 3 Hierarchy transactions (old parent + child + new parent), got " + hierarchyChanges.size());
        }
    }

    @Nested
    class MaterialProperties {
        static final PropertyKey<MaterialData, Float> ROUGHNESS = PropertyKey.of("roughness", Float.class);

        @Test void setMaterialPropertyEmitsTransaction() {
            var entity = scene.createEntity();
            drainTransactions(scene);
            scene.setMaterialProperty(entity, ROUGHNESS, 0.5f);
            var txns = drainTransactions(scene);
            assertEquals(1, txns.size());
            assertInstanceOf(Transaction.MaterialPropertyChanged.class, txns.getFirst());
        }
    }
}
