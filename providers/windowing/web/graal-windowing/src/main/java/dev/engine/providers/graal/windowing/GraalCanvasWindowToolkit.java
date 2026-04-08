package dev.engine.providers.graal.windowing;

import dev.engine.graphics.window.WindowDescriptor;
import dev.engine.graphics.window.WindowHandle;
import dev.engine.graphics.window.WindowToolkit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;

/**
 * Browser canvas-based {@link WindowToolkit} for GraalJS.
 *
 * <p>{@link #pollEvents()} yields to the browser event loop by awaiting
 * {@code requestAnimationFrame}, matching the pattern used by the TeaVM
 * canvas toolkit. This allows the standard {@code while (window.isOpen())}
 * game loop in {@code BaseApplication} to work on the web.
 *
 * <p>Requires a shared GraalJS {@link Context} with DOM access.
 */
public class GraalCanvasWindowToolkit implements WindowToolkit {

    private final Context context;
    private final Value bridge;

    public GraalCanvasWindowToolkit(Context context) {
        this.context = context;
        this.bridge = context.eval("js", BRIDGE_JS);
    }

    @Override
    public WindowHandle createWindow(WindowDescriptor descriptor) {
        bridge.getMember("setTitle").executeVoid(descriptor.title());
        return new GraalCanvasWindowHandle(bridge);
    }

    @Override
    public void pollEvents() {
        // Yield to browser via requestAnimationFrame.
        // Evaluated as ES module to support top-level await.
        try {
            Source raf = Source.newBuilder("js", RAF_JS, "raf.mjs")
                    .mimeType("application/javascript+module")
                    .build();
            context.eval(raf);
        } catch (IOException e) {
            throw new RuntimeException("Failed to yield to requestAnimationFrame", e);
        }
    }

    @Override
    public void close() {}

    // =====================================================================

    private static final class GraalCanvasWindowHandle implements WindowHandle {

        private final Value bridge;
        private boolean open = true;

        GraalCanvasWindowHandle(Value bridge) {
            this.bridge = bridge;
        }

        @Override
        public boolean isOpen() { return open; }

        @Override
        public int width() { return bridge.getMember("getWidth").execute().asInt(); }

        @Override
        public int height() { return bridge.getMember("getHeight").execute().asInt(); }

        @Override
        public String title() { return bridge.getMember("getTitle").execute().asString(); }

        @Override
        public void show() {}

        @Override
        public long nativeHandle() { return 0; }

        @Override
        public void close() { open = false; }
    }

    // =====================================================================

    private static final String BRIDGE_JS = """
            (function() {
                return {
                    setTitle: function(title) { document.title = title; },
                    getTitle: function() { return document.title; },
                    getWidth: function() { return document.getElementById('canvas').width; },
                    getHeight: function() { return document.getElementById('canvas').height; }
                };
            })()
            """;

    /** Top-level await on requestAnimationFrame — yields to the browser event loop. */
    private static final String RAF_JS = """
            await new Promise(function(resolve) {
                if (document.hidden) { setTimeout(resolve, 16); }
                else { requestAnimationFrame(function() { resolve(); }); }
            });
            """;
}
