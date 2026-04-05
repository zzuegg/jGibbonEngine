package dev.engine.bindings.sdl3;

import dev.engine.core.input.*;
import dev.engine.core.module.Time;
import org.lwjgl.sdl.SDL_Event;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDLKeyboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SDL3 input provider. Processes SDL events during poll and pushes
 * InputEvent/WindowEvent records into the InputSystem's queue.
 *
 * <p>This provider processes events from the same SDL_PollEvent loop
 * as Sdl3WindowToolkit. Call {@link #processEvent(SDL_Event)} for each
 * polled event.
 */
public class Sdl3InputProvider implements InputProvider {

    private static final Logger log = LoggerFactory.getLogger(Sdl3InputProvider.class);

    private static final DeviceId KEYBOARD = new DeviceId(DeviceType.KEYBOARD, 0);
    private static final DeviceId MOUSE = new DeviceId(DeviceType.MOUSE, 0);

    private InputSystem system;
    private InputEventQueue queue;
    private Time currentTime = new Time(0, 0);

    public void setTime(Time time) {
        this.currentTime = time;
    }

    @Override
    public void initialize(InputSystem system) {
        this.system = system;
        this.queue = system.queue();
        log.info("SDL3 input provider initialized");
    }

    /**
     * Process a single SDL event. Called from the SDL event polling loop.
     */
    public void processEvent(SDL_Event event) {
        if (queue == null) return;

        int type = event.type();
        switch (type) {
            case SDLEvents.SDL_EVENT_KEY_DOWN -> {
                var kb = event.key();
                var keyCode = Sdl3KeyMapping.mapKey(kb.key());
                var mods = Sdl3KeyMapping.mapModifiers(kb.mod());
                var sc = new ScanCode(kb.scancode());
                if (kb.repeat()) {
                    queue.pushInput(new InputEvent.KeyRepeated(currentTime, KEYBOARD, keyCode, sc, mods));
                } else {
                    queue.pushInput(new InputEvent.KeyPressed(currentTime, KEYBOARD, keyCode, sc, mods));
                }
            }
            case SDLEvents.SDL_EVENT_KEY_UP -> {
                var kb = event.key();
                var keyCode = Sdl3KeyMapping.mapKey(kb.key());
                var mods = Sdl3KeyMapping.mapModifiers(kb.mod());
                var sc = new ScanCode(kb.scancode());
                queue.pushInput(new InputEvent.KeyReleased(currentTime, KEYBOARD, keyCode, sc, mods));
            }
            case SDLEvents.SDL_EVENT_MOUSE_BUTTON_DOWN -> {
                var mb = event.button();
                var button = Sdl3KeyMapping.mapMouseButton(mb.button());
                var mods = Sdl3KeyMapping.mapModifiers(SDLKeyboard.SDL_GetModState());
                queue.pushInput(new InputEvent.MousePressed(currentTime, MOUSE, button, mods, mb.x(), mb.y()));
            }
            case SDLEvents.SDL_EVENT_MOUSE_BUTTON_UP -> {
                var mb = event.button();
                var button = Sdl3KeyMapping.mapMouseButton(mb.button());
                var mods = Sdl3KeyMapping.mapModifiers(SDLKeyboard.SDL_GetModState());
                queue.pushInput(new InputEvent.MouseReleased(currentTime, MOUSE, button, mods, mb.x(), mb.y()));
            }
            case SDLEvents.SDL_EVENT_MOUSE_MOTION -> {
                var motion = event.motion();
                var mods = Sdl3KeyMapping.mapModifiers(SDLKeyboard.SDL_GetModState());
                queue.pushInput(new InputEvent.CursorMoved(currentTime, MOUSE, mods, motion.x(), motion.y()));
            }
            case SDLEvents.SDL_EVENT_MOUSE_WHEEL -> {
                var wheel = event.wheel();
                var mods = Sdl3KeyMapping.mapModifiers(SDLKeyboard.SDL_GetModState());
                queue.pushInput(new InputEvent.Scrolled(currentTime, MOUSE, mods, wheel.x(), wheel.y()));
            }
            case SDLEvents.SDL_EVENT_TEXT_INPUT -> {
                var text = event.text().textString();
                if (text != null) {
                    for (int i = 0; i < text.length(); ) {
                        int cp = text.codePointAt(i);
                        queue.pushInput(new InputEvent.CharTyped(currentTime, KEYBOARD, new Codepoint(cp)));
                        i += Character.charCount(cp);
                    }
                }
            }
            case SDLEvents.SDL_EVENT_WINDOW_RESIZED -> {
                var win = event.window();
                queue.pushWindow(new WindowEvent.Resized(currentTime, win.data1(), win.data2()));
            }
            case SDLEvents.SDL_EVENT_WINDOW_FOCUS_GAINED ->
                queue.pushWindow(new WindowEvent.FocusGained(currentTime));
            case SDLEvents.SDL_EVENT_WINDOW_FOCUS_LOST ->
                queue.pushWindow(new WindowEvent.FocusLost(currentTime));
            case SDLEvents.SDL_EVENT_WINDOW_MOVED -> {
                var win = event.window();
                queue.pushWindow(new WindowEvent.Moved(currentTime, win.data1(), win.data2()));
            }
            case SDLEvents.SDL_EVENT_WINDOW_CLOSE_REQUESTED ->
                queue.pushWindow(new WindowEvent.Closed(currentTime));
            default -> {} // ignore unhandled events
        }
    }

    @Override
    public void poll() {
        // Events are processed via processEvent() called from Sdl3WindowToolkit.pollEvents()
    }

    @Override
    public void close() {
        // Nothing to clean up
    }
}
