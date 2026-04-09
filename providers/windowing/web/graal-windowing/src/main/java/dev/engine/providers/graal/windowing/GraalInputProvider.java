package dev.engine.providers.graal.windowing;

import dev.engine.core.input.*;
import dev.engine.core.module.Time;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GraalVM Web Image input provider. Registers JavaScript DOM event listeners
 * on the canvas element and pushes {@link InputEvent}s into the queue.
 *
 * <p>Uses a JS-side event buffer: DOM listeners push events into an array,
 * and {@link #poll()} drains them into the Java queue each frame.
 */
public class GraalInputProvider implements InputProvider {

    private static final Logger log = LoggerFactory.getLogger(GraalInputProvider.class);

    private static final DeviceId KEYBOARD = new DeviceId(DeviceType.KEYBOARD, 0);
    private static final DeviceId MOUSE = new DeviceId(DeviceType.MOUSE, 0);

    private InputEventQueue queue;
    private Time currentTime = new Time(0, 0);

    public void setTime(Time time) { this.currentTime = time; }

    @Override
    public void initialize(InputSystem system) {
        this.queue = system.queue();
        registerListeners();
        log.info("GraalJS input provider initialized");
    }

    @Override
    public void poll() {
        int count = getEventCount();
        for (int i = 0; i < count; i++) {
            String type = getEventType(i).asString();
            processEvent(type, i);
        }
        clearEvents();
    }

    @Override
    public void close() {}

    private void processEvent(String type, int idx) {
        switch (type) {
            case "kd" -> {
                var keyCode = GraalKeyMapping.mapKey(getEventString(idx, 1).asString());
                int mods = getEventInt(idx, 2);
                queue.pushInput(new InputEvent.KeyPressed(currentTime, KEYBOARD,
                        keyCode, new ScanCode(0), new Modifiers(mods)));
            }
            case "ku" -> {
                var keyCode = GraalKeyMapping.mapKey(getEventString(idx, 1).asString());
                int mods = getEventInt(idx, 2);
                queue.pushInput(new InputEvent.KeyReleased(currentTime, KEYBOARD,
                        keyCode, new ScanCode(0), new Modifiers(mods)));
            }
            case "md" -> {
                var mb = GraalKeyMapping.mapMouseButton(getEventInt(idx, 1));
                double x = getEventDouble(idx, 2);
                double y = getEventDouble(idx, 3);
                int mods = getEventInt(idx, 4);
                queue.pushInput(new InputEvent.MousePressed(currentTime, MOUSE,
                        mb, new Modifiers(mods), x, y));
            }
            case "mu" -> {
                var mb = GraalKeyMapping.mapMouseButton(getEventInt(idx, 1));
                double x = getEventDouble(idx, 2);
                double y = getEventDouble(idx, 3);
                int mods = getEventInt(idx, 4);
                queue.pushInput(new InputEvent.MouseReleased(currentTime, MOUSE,
                        mb, new Modifiers(mods), x, y));
            }
            case "mm" -> {
                double x = getEventDouble(idx, 1);
                double y = getEventDouble(idx, 2);
                queue.pushInput(new InputEvent.CursorMoved(currentTime, MOUSE,
                        new Modifiers(0), x, y));
            }
            case "wh" -> {
                double dx = getEventDouble(idx, 1);
                double dy = getEventDouble(idx, 2);
                queue.pushInput(new InputEvent.Scrolled(currentTime, MOUSE,
                        new Modifiers(0), dx, dy));
            }
        }
    }

    // --- @JS native methods ---

    @JS(value = """
        if (globalThis._inputBuf) return;
        globalThis._inputBuf = [];
        var mods = function(e) {
            return (e.shiftKey?1:0)|(e.ctrlKey?2:0)|(e.altKey?4:0)|(e.metaKey?8:0);
        };
        var pk = {'Tab':1,'Space':1,'ArrowUp':1,'ArrowDown':1,'ArrowLeft':1,'ArrowRight':1};
        document.addEventListener('keydown', function(e) {
            globalThis._inputBuf.push(['kd', e.code, mods(e)]);
            if (pk[e.key]) e.preventDefault();
        });
        document.addEventListener('keyup', function(e) {
            globalThis._inputBuf.push(['ku', e.code, mods(e)]);
        });
        var c = document.querySelector('canvas');
        if (c) {
            c.addEventListener('mousedown', function(e) {
                globalThis._inputBuf.push(['md', e.button, e.offsetX, e.offsetY, mods(e)]);
            });
            c.addEventListener('mouseup', function(e) {
                globalThis._inputBuf.push(['mu', e.button, e.offsetX, e.offsetY, mods(e)]);
            });
            c.addEventListener('mousemove', function(e) {
                globalThis._inputBuf.push(['mm', e.offsetX, e.offsetY]);
            });
            c.addEventListener('wheel', function(e) {
                globalThis._inputBuf.push(['wh', e.deltaX, e.deltaY]);
                e.preventDefault();
            }, {passive:false});
        }
    """)
    private static native void registerListeners();

    private static int getEventCount() { return getEventCountJS().asInt(); }

    @JS(value = "return globalThis._inputBuf ? globalThis._inputBuf.length : 0;")
    private static native JSNumber getEventCountJS();

    @JS(args = "idx", value = "return '' + globalThis._inputBuf[idx][0];")
    private static native JSString getEventType(int idx);

    @JS(args = {"idx", "field"}, value = "return '' + globalThis._inputBuf[idx][field];")
    private static native JSString getEventString(int idx, int field);

    private static int getEventInt(int idx, int field) { return getEventIntJS(idx, field).asInt(); }

    @JS(args = {"idx", "field"}, value = "return globalThis._inputBuf[idx][field];")
    private static native JSNumber getEventIntJS(int idx, int field);

    private static double getEventDouble(int idx, int field) { return getEventDoubleJS(idx, field).asDouble(); }

    @JS(args = {"idx", "field"}, value = "return globalThis._inputBuf[idx][field];")
    private static native JSNumber getEventDoubleJS(int idx, int field);

    @JS(value = "globalThis._inputBuf = [];")
    private static native void clearEvents();
}
