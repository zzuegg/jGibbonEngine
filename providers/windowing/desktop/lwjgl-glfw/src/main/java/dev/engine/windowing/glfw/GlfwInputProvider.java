package dev.engine.windowing.glfw;

import dev.engine.core.input.*;
import dev.engine.core.module.Time;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GLFW input provider. Registers callbacks on a GLFW window and pushes
 * events into the InputSystem's event queue.
 */
public class GlfwInputProvider implements InputProvider {

    private static final Logger log = LoggerFactory.getLogger(GlfwInputProvider.class);

    private static final DeviceId KEYBOARD = new DeviceId(DeviceType.KEYBOARD, 0);
    private static final DeviceId MOUSE = new DeviceId(DeviceType.MOUSE, 0);

    private final GlfwWindowToolkit.GlfwWindowHandle window;
    private InputSystem system;
    private InputEventQueue queue;
    private Time currentTime = new Time(0, 0);

    public GlfwInputProvider(GlfwWindowToolkit.GlfwWindowHandle window) {
        this.window = window;
    }

    /** Call each frame before poll() to set the current engine time for events. */
    public void setTime(Time time) {
        this.currentTime = time;
    }

    @Override
    public void initialize(InputSystem system) {
        this.system = system;
        this.queue = system.queue();
        long handle = window.nativeHandle();

        GLFW.glfwSetKeyCallback(handle, (win, key, scancode, action, mods) -> {
            var keyCode = GlfwKeyMapping.mapKey(key);
            var sc = new ScanCode(scancode);
            var modifiers = GlfwKeyMapping.mapModifiers(mods);
            switch (action) {
                case GLFW.GLFW_PRESS -> queue.pushInput(new InputEvent.KeyPressed(currentTime, KEYBOARD, keyCode, sc, modifiers));
                case GLFW.GLFW_RELEASE -> queue.pushInput(new InputEvent.KeyReleased(currentTime, KEYBOARD, keyCode, sc, modifiers));
                case GLFW.GLFW_REPEAT -> queue.pushInput(new InputEvent.KeyRepeated(currentTime, KEYBOARD, keyCode, sc, modifiers));
            }
        });

        GLFW.glfwSetMouseButtonCallback(handle, (win, button, action, mods) -> {
            var mb = GlfwKeyMapping.mapMouseButton(button);
            var modifiers = GlfwKeyMapping.mapModifiers(mods);
            double[] xPos = new double[1], yPos = new double[1];
            GLFW.glfwGetCursorPos(handle, xPos, yPos);
            switch (action) {
                case GLFW.GLFW_PRESS -> queue.pushInput(new InputEvent.MousePressed(currentTime, MOUSE, mb, modifiers, xPos[0], yPos[0]));
                case GLFW.GLFW_RELEASE -> queue.pushInput(new InputEvent.MouseReleased(currentTime, MOUSE, mb, modifiers, xPos[0], yPos[0]));
            }
        });

        GLFW.glfwSetCursorPosCallback(handle, (win, xpos, ypos) -> {
            int mods = getCurrentModifiers(handle);
            queue.pushInput(new InputEvent.CursorMoved(currentTime, MOUSE, new Modifiers(mods), xpos, ypos));
        });

        GLFW.glfwSetScrollCallback(handle, (win, xoffset, yoffset) -> {
            int mods = getCurrentModifiers(handle);
            queue.pushInput(new InputEvent.Scrolled(currentTime, MOUSE, new Modifiers(mods), xoffset, yoffset));
        });

        GLFW.glfwSetCharCallback(handle, (win, codepoint) -> {
            queue.pushInput(new InputEvent.CharTyped(currentTime, KEYBOARD, new Codepoint(codepoint)));
        });

        GLFW.glfwSetWindowSizeCallback(handle, (win, width, height) -> {
            window.updateSize(width, height);
            queue.pushWindow(new WindowEvent.Resized(currentTime, width, height));
        });

        GLFW.glfwSetWindowFocusCallback(handle, (win, focused) -> {
            window.updateFocused(focused);
            if (focused) {
                queue.pushWindow(new WindowEvent.FocusGained(currentTime));
            } else {
                queue.pushWindow(new WindowEvent.FocusLost(currentTime));
            }
        });

        GLFW.glfwSetWindowPosCallback(handle, (win, xpos, ypos) -> {
            queue.pushWindow(new WindowEvent.Moved(currentTime, xpos, ypos));
        });

        GLFW.glfwSetWindowCloseCallback(handle, (win) -> {
            queue.pushWindow(new WindowEvent.Closed(currentTime));
        });

        log.info("GLFW input provider initialized");
    }

    @Override
    public void poll() {
        // GLFW callbacks fire during glfwPollEvents() -- nothing to do here
        // since GlfwWindowToolkit.pollEvents() already calls glfwPollEvents()
    }

    @Override
    public void close() {
        // Callbacks are cleaned up when the window is destroyed
    }

    private static int getCurrentModifiers(long handle) {
        int mods = 0;
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) {
            mods |= 0x01;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) {
            mods |= 0x02;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS) {
            mods |= 0x04;
        }
        if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SUPER) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SUPER) == GLFW.GLFW_PRESS) {
            mods |= 0x08;
        }
        return mods;
    }
}
