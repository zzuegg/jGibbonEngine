package dev.engine.graphics.resource;

import dev.engine.graphics.BufferResource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResourceRegistryTest {
    @Test void registerAndGet() {
        var reg = new ResourceRegistry<BufferResource, Integer>("buffer");
        var handle = reg.register(42);
        assertEquals(42, reg.get(handle));
        assertTrue(reg.isValid(handle));
    }

    @Test void removeReleasesHandle() {
        var reg = new ResourceRegistry<BufferResource, Integer>("buffer");
        var handle = reg.register(42);
        var removed = reg.remove(handle);
        assertEquals(42, removed);
        assertFalse(reg.isValid(handle));
    }

    @Test void sizeTracksAllocations() {
        var reg = new ResourceRegistry<BufferResource, Integer>("buffer");
        assertEquals(0, reg.size());
        var h1 = reg.register(1);
        var h2 = reg.register(2);
        assertEquals(2, reg.size());
        reg.remove(h1);
        assertEquals(1, reg.size());
    }

    @Test void destroyAllCleansUp() {
        var reg = new ResourceRegistry<BufferResource, Integer>("buffer");
        reg.register(1);
        reg.register(2);
        var destroyed = new java.util.ArrayList<Integer>();
        reg.destroyAll(destroyed::add);
        assertEquals(2, destroyed.size());
    }

    @Test void allocateWithoutData() {
        var reg = new ResourceRegistry<BufferResource, Integer>("buffer");
        var handle = reg.allocate();
        assertNull(reg.get(handle));
        reg.put(handle, 99);
        assertEquals(99, reg.get(handle));
    }
}
