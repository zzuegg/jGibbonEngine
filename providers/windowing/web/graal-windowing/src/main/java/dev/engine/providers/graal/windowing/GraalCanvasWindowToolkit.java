package dev.engine.providers.graal.windowing;

import dev.engine.core.input.InputProvider;
import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSString;

/**
 * Browser canvas-based {@link WindowToolkit} for GraalVM Web Image.
 *
 * <p>Uses {@code @JS} annotations to call DOM APIs. The game loop frame pacing
 * is handled by the host HTML page (not by pollEvents), since Web Image
 * {@code @JS} methods are synchronous.
 */
public class GraalCanvasWindowToolkit implements WindowToolkit {

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        setDocumentTitle(descriptor.title());
        return new GraalCanvasWindowHandle();
    }

    @Override
    public void pollEvents() {
        // In Web Image, yielding to the browser is handled by the host page.
        // For screenshot tests, frames are driven synchronously from Java.
    }

    @Override
    public InputProvider createInputProvider(WindowHandle window) {
        return new GraalInputProvider();
    }

    @Override
    public void close() {}

    @JS(args = "title", value = "document.title = title;")
    private static native void setDocumentTitle(String title);

    // --- Window handle ---

    private static final class GraalCanvasWindowHandle implements WindowHandle {
        private boolean open = true;

        @Override public boolean isOpen() { return open; }
        @Override public int width() { return getCanvasWidth().asInt(); }
        @Override public int height() { return getCanvasHeight().asInt(); }
        @Override public String title() { return getDocTitle().asString(); }
        @Override public void show() {}
        @Override public long nativeHandle() { return 0; }
        @Override public void close() { open = false; }

        @JS(value = "return document.getElementById('canvas').width;")
        private static native JSNumber getCanvasWidth();

        @JS(value = "return document.getElementById('canvas').height;")
        private static native JSNumber getCanvasHeight();

        @JS(value = "return document.title;")
        private static native JSString getDocTitle();
    }
}
