package dev.engine.providers.teavm.windowing;

import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;

/**
 * Browser canvas-based {@link WindowToolkit} for TeaVM.
 *
 * <p>Creates a {@link WindowHandle} backed by an HTML {@code <canvas>}
 * element. The canvas is located by ID ("canvas" by default) rather than
 * created programmatically, so the hosting HTML page must contain a
 * matching element.
 *
 * <p><b>Status:</b> Stub — enough to satisfy the interface so the TeaVM
 * build pipeline compiles. Canvas integration will be wired up via JSO
 * in a follow-up.
 */
public class CanvasWindowToolkit implements WindowToolkit {

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        return new CanvasWindowHandle(descriptor);
    }

    @Override
    public void pollEvents() {
        // No-op in browser — events are delivered via the DOM event loop
    }

    @Override
    public void close() {
        // Nothing to release
    }

    // ------------------------------------------------------------------

    private static final class CanvasWindowHandle implements WindowHandle {

        private final String title;
        private final int width;
        private final int height;
        private boolean open = true;

        CanvasWindowHandle(WindowDescriptor descriptor) {
            this.title = descriptor.title();
            this.width = descriptor.width();
            this.height = descriptor.height();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public String title() {
            return title;
        }

        @Override
        public void show() {
            // Canvas is always visible in the page
        }

        @Override
        public long nativeHandle() {
            // No native handle in browser context
            return 0;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
