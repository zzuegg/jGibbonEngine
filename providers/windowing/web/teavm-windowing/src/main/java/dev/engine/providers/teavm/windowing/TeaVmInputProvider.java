package dev.engine.providers.teavm.windowing;

import dev.engine.core.input.*;
import dev.engine.core.module.Time;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TeaVM/browser input provider. Registers JavaScript DOM event listeners
 * on the canvas element and pushes InputEvents into the queue.
 */
public class TeaVmInputProvider implements InputProvider {

    private static final Logger log = LoggerFactory.getLogger(TeaVmInputProvider.class);

    private static final DeviceId KEYBOARD = new DeviceId(DeviceType.KEYBOARD, 0);
    private static final DeviceId MOUSE = new DeviceId(DeviceType.MOUSE, 0);

    private InputEventQueue queue;
    private Time currentTime = new Time(0, 0);

    public void setTime(Time time) {
        this.currentTime = time;
    }

    @Override
    public void initialize(InputSystem system) {
        this.queue = system.queue();
        registerEventListeners();
        log.info("TeaVM input provider initialized");
    }

    @Override
    public void poll() {
        // DOM events fire asynchronously via JS listeners -- nothing to poll
    }

    @Override
    public void close() {
        // Listeners are GC'd with the page
    }

    private void registerEventListeners() {
        registerKeyDown((code, mods) -> {
            var keyCode = TeaVmKeyMapping.mapKey(code);
            queue.pushInput(new InputEvent.KeyPressed(currentTime, KEYBOARD, keyCode, new ScanCode(0), new Modifiers(mods)));
        });

        registerKeyUp((code, mods) -> {
            var keyCode = TeaVmKeyMapping.mapKey(code);
            queue.pushInput(new InputEvent.KeyReleased(currentTime, KEYBOARD, keyCode, new ScanCode(0), new Modifiers(mods)));
        });

        registerMouseDown((button, x, y, mods) -> {
            var mb = TeaVmKeyMapping.mapMouseButton(button);
            queue.pushInput(new InputEvent.MousePressed(currentTime, MOUSE, mb, new Modifiers(mods), x, y));
        });

        registerMouseUp((button, x, y, mods) -> {
            var mb = TeaVmKeyMapping.mapMouseButton(button);
            queue.pushInput(new InputEvent.MouseReleased(currentTime, MOUSE, mb, new Modifiers(mods), x, y));
        });

        registerMouseMove((x, y) -> {
            queue.pushInput(new InputEvent.CursorMoved(currentTime, MOUSE, new Modifiers(0), x, y));
        });

        registerWheel((dx, dy) -> {
            queue.pushInput(new InputEvent.Scrolled(currentTime, MOUSE, new Modifiers(0), dx, dy));
        });
    }

    // --- JS interop ---

    @JSFunctor
    interface KeyCallback extends JSObject {
        void onKey(String code, int mods);
    }

    @JSFunctor
    interface MouseButtonCallback extends JSObject {
        void onButton(int button, double x, double y, int mods);
    }

    @JSFunctor
    interface MouseMoveCallback extends JSObject {
        void onMove(double x, double y);
    }

    @JSFunctor
    interface WheelCallback extends JSObject {
        void onWheel(double dx, double dy);
    }

    @JSBody(params = "callback", script = """
        document.addEventListener('keydown', function(e) {
            var mods = (e.shiftKey ? 1 : 0) | (e.ctrlKey ? 2 : 0) | (e.altKey ? 4 : 0) | (e.metaKey ? 8 : 0);
            callback(e.code, mods);
            if (['Tab', 'Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].indexOf(e.key) >= 0) {
                e.preventDefault();
            }
        });
    """)
    private static native void registerKeyDown(KeyCallback callback);

    @JSBody(params = "callback", script = """
        document.addEventListener('keyup', function(e) {
            var mods = (e.shiftKey ? 1 : 0) | (e.ctrlKey ? 2 : 0) | (e.altKey ? 4 : 0) | (e.metaKey ? 8 : 0);
            callback(e.code, mods);
        });
    """)
    private static native void registerKeyUp(KeyCallback callback);

    @JSBody(params = "callback", script = """
        var canvas = document.querySelector('canvas');
        if (canvas) {
            canvas.addEventListener('mousedown', function(e) {
                var mods = (e.shiftKey ? 1 : 0) | (e.ctrlKey ? 2 : 0) | (e.altKey ? 4 : 0) | (e.metaKey ? 8 : 0);
                callback(e.button, e.offsetX, e.offsetY, mods);
            });
        }
    """)
    private static native void registerMouseDown(MouseButtonCallback callback);

    @JSBody(params = "callback", script = """
        var canvas = document.querySelector('canvas');
        if (canvas) {
            canvas.addEventListener('mouseup', function(e) {
                var mods = (e.shiftKey ? 1 : 0) | (e.ctrlKey ? 2 : 0) | (e.altKey ? 4 : 0) | (e.metaKey ? 8 : 0);
                callback(e.button, e.offsetX, e.offsetY, mods);
            });
        }
    """)
    private static native void registerMouseUp(MouseButtonCallback callback);

    @JSBody(params = "callback", script = """
        var canvas = document.querySelector('canvas');
        if (canvas) {
            canvas.addEventListener('mousemove', function(e) {
                callback(e.offsetX, e.offsetY);
            });
        }
    """)
    private static native void registerMouseMove(MouseMoveCallback callback);

    @JSBody(params = "callback", script = """
        var canvas = document.querySelector('canvas');
        if (canvas) {
            canvas.addEventListener('wheel', function(e) {
                callback(e.deltaX, e.deltaY);
                e.preventDefault();
            }, {passive: false});
        }
    """)
    private static native void registerWheel(WheelCallback callback);
}
