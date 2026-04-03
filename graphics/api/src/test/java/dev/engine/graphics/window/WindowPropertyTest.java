package dev.engine.graphics.window;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WindowPropertyTest {
    @Test void keysHaveCorrectTypes() {
        assertEquals(String.class, WindowProperty.TITLE.type());
        assertEquals(Boolean.class, WindowProperty.VSYNC.type());
        assertEquals(Boolean.class, WindowProperty.RESIZABLE.type());
        assertEquals(Boolean.class, WindowProperty.FULLSCREEN.type());
        assertEquals(Integer.class, WindowProperty.SWAP_INTERVAL.type());
    }

    @Test void keysAreDistinct() {
        assertNotEquals(WindowProperty.TITLE, WindowProperty.VSYNC);
        assertNotEquals(WindowProperty.RESIZABLE, WindowProperty.FULLSCREEN);
    }
}
