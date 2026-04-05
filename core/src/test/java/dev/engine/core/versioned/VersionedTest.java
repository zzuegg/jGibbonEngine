package dev.engine.core.versioned;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VersionedTest {

    @Nested
    class BasicBehavior {
        @Test void initialValueIsAccessible() {
            var v = new Versioned<>("hello");
            assertEquals("hello", v.getValue());
        }

        @Test void nullInitialValue() {
            var v = new Versioned<String>(null);
            assertNull(v.getValue());
        }

        @Test void setUpdatesValue() {
            var v = new Versioned<>("hello");
            v.set("world");
            assertEquals("world", v.getValue());
        }
    }

    @Nested
    class ReferenceTracking {
        @Test void referenceDetectsInitialValue() {
            var v = new Versioned<>("hello");
            var ref = v.createReference();
            assertTrue(ref.update(), "First update should detect initial value");
            assertEquals("hello", ref.getValue());
        }

        @Test void referenceDetectsChange() {
            var v = new Versioned<>("hello");
            var ref = v.createReference();
            ref.update(); // consume initial

            v.set("world");
            assertTrue(ref.update(), "Should detect change");
            assertEquals("world", ref.getValue());
        }

        @Test void referenceReturnsFalseWhenUnchanged() {
            var v = new Versioned<>("hello");
            var ref = v.createReference();
            ref.update(); // consume initial

            assertFalse(ref.update(), "No change should return false");
        }

        @Test void multipleReferencesTrackIndependently() {
            var v = new Versioned<>(1);
            var ref1 = v.createReference();
            var ref2 = v.createReference();

            ref1.update(); // ref1 consumes initial
            v.set(2);

            assertTrue(ref1.update(), "ref1 should detect change to 2");
            assertEquals(2, ref1.getValue());

            // ref2 hasn't consumed anything yet -- should detect the change
            assertTrue(ref2.update(), "ref2 should detect change");
            assertEquals(2, ref2.getValue());
        }

        @Test void referenceCanWriteBack() {
            var v = new Versioned<>("hello");
            var ref = v.createReference();
            ref.set("world");
            assertEquals("world", v.getValue());
        }

        @Test void referenceCanCreateSibling() {
            var v = new Versioned<>(42);
            var ref1 = v.createReference();
            var ref2 = ref1.reference();

            ref1.update(); // consume
            v.set(99);

            assertTrue(ref2.update(), "Sibling should independently track");
            assertEquals(99, ref2.getValue());
        }

        @Test void nullInitialValueDetectedOnFirstUpdate() {
            var v = new Versioned<String>(null);
            var ref = v.createReference();
            // null initial value: version 0, ref starts at 0 -- no change
            assertFalse(ref.update(), "Null initial should not trigger update");
        }

        @Test void settingNullAfterValueIsDetected() {
            var v = new Versioned<>("hello");
            var ref = v.createReference();
            ref.update(); // consume initial

            v.set(null);
            assertTrue(ref.update(), "Setting null should be detected as change");
            assertNull(ref.getValue());
        }
    }
}
