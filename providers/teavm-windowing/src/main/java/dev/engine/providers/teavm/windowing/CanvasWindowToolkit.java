package dev.engine.providers.teavm.windowing;

import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;

/**
 * Browser canvas-based {@link WindowToolkit} for TeaVM.
 *
 * <p>{@link #pollEvents()} suspends the TeaVM thread until the next
 * {@code requestAnimationFrame} fires, yielding to the browser event loop.
 * This allows the standard {@code while (window.isOpen())} loop in
 * {@link dev.engine.graphics.common.engine.BaseApplication} to work
 * identically on desktop and web.
 */
public class CanvasWindowToolkit implements WindowToolkit {

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        return new CanvasWindowHandle(descriptor);
    }

    /**
     * Yields to the browser by waiting for the next {@code requestAnimationFrame}.
     * This is the web equivalent of GLFW's poll events + vsync.
     */
    @Override
    public void pollEvents() {
        waitForAnimationFrame();
    }

    @Override
    public void close() {
        // Nothing to release
    }

    // --- requestAnimationFrame as @Async ---

    @Async
    private static native void waitForAnimationFrame();

    private static void waitForAnimationFrame(AsyncCallback<Void> callback) {
        waitForAnimationFrameJS(callback);
    }

    @JSBody(params = "callback", script = "requestAnimationFrame(function() { callback(null); });")
    private static native void waitForAnimationFrameJS(AsyncCallback<Void> callback);

    // ------------------------------------------------------------------

    private static final class CanvasWindowHandle implements WindowHandle {

        private final String canvasId;
        private boolean open = true;

        CanvasWindowHandle(WindowDescriptor descriptor) {
            this.canvasId = "canvas"; // default canvas element ID
            setCanvasTitle(descriptor.title());
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public int width() {
            return getCanvasWidth(canvasId);
        }

        @Override
        public int height() {
            return getCanvasHeight(canvasId);
        }

        @Override
        public String title() {
            return getDocumentTitle();
        }

        @Override
        public void show() {
            // Canvas is always visible
        }

        @Override
        public long nativeHandle() {
            return 0;
        }

        @Override
        public void close() {
            open = false;
        }

        @JSBody(params = "title", script = "document.title = title;")
        private static native void setCanvasTitle(String title);

        @JSBody(script = "return document.title;")
        private static native String getDocumentTitle();

        @JSBody(params = "id", script = "return document.getElementById(id).width;")
        private static native int getCanvasWidth(String id);

        @JSBody(params = "id", script = "return document.getElementById(id).height;")
        private static native int getCanvasHeight(String id);
    }
}
