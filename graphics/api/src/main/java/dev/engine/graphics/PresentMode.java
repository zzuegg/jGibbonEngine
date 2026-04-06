package dev.engine.graphics;

/**
 * Presentation mode controlling vsync behavior.
 *
 * <ul>
 *   <li>{@link #FIFO} — VSync on. Frames queued in order, displayed at monitor refresh rate.</li>
 *   <li>{@link #IMMEDIATE} — VSync off. Frames presented immediately, may tear on X11.</li>
 *   <li>{@link #MAILBOX} — Triple-buffered. Uncapped rendering, only latest frame displayed at refresh. No tearing.</li>
 * </ul>
 *
 * <p>Availability depends on the backend and platform:
 * <ul>
 *   <li>OpenGL: FIFO = swapInterval(1), IMMEDIATE = swapInterval(0), MAILBOX = swapInterval(-1) (adaptive, not always available)</li>
 *   <li>Vulkan: all three modes supported on X11; IMMEDIATE unavailable on Wayland (falls back to MAILBOX)</li>
 *   <li>WebGPU: IMMEDIATE unavailable on Wayland (falls back to MAILBOX)</li>
 * </ul>
 */
public enum PresentMode {
    FIFO,
    IMMEDIATE,
    MAILBOX
}
