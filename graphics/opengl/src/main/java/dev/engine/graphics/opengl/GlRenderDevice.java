package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.RenderCapability;
import dev.engine.graphics.RenderContext;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL45;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class GlRenderDevice implements RenderDevice {

    private static final Logger log = LoggerFactory.getLogger(GlRenderDevice.class);

    private final HandlePool bufferPool = new HandlePool();
    private final Map<Integer, Integer> bufferGlNames = new HashMap<>(); // handle index -> GL buffer name
    private final AtomicLong frameCounter = new AtomicLong(0);
    private final long glfwWindow;

    public GlRenderDevice(GlfwWindowToolkit.GlfwWindowHandle window) {
        this.glfwWindow = window.glfwHandle();
        GLFW.glfwMakeContextCurrent(glfwWindow);
        GL.createCapabilities();
        log.info("OpenGL context created: {}", GL45.glGetString(GL45.GL_VERSION));
    }

    @Override
    public Handle createBuffer(BufferDescriptor descriptor) {
        int glBuffer = GL45.glCreateBuffers();
        int usage = mapUsage(descriptor.accessPattern());
        GL45.glNamedBufferData(glBuffer, descriptor.size(), usage);

        var handle = bufferPool.allocate();
        bufferGlNames.put(handle.index(), glBuffer);
        return handle;
    }

    @Override
    public void destroyBuffer(Handle buffer) {
        if (!bufferPool.isValid(buffer)) return;
        Integer glName = bufferGlNames.remove(buffer.index());
        if (glName != null) {
            GL45.glDeleteBuffers(glName);
        }
        bufferPool.release(buffer);
    }

    @Override
    public boolean isValidBuffer(Handle buffer) {
        return bufferPool.isValid(buffer);
    }

    @Override
    public RenderContext beginFrame() {
        long frame = frameCounter.incrementAndGet();
        return () -> frame;
    }

    @Override
    public void endFrame(RenderContext context) {
        GLFW.glfwSwapBuffers(glfwWindow);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T queryCapability(RenderCapability<T> capability) {
        if (capability == RenderCapability.MAX_TEXTURE_SIZE) {
            return (T) Integer.valueOf(GL45.glGetInteger(GL45.GL_MAX_TEXTURE_SIZE));
        }
        if (capability == RenderCapability.MAX_FRAMEBUFFER_WIDTH) {
            return (T) Integer.valueOf(GL45.glGetInteger(GL45.GL_MAX_FRAMEBUFFER_WIDTH));
        }
        if (capability == RenderCapability.MAX_FRAMEBUFFER_HEIGHT) {
            return (T) Integer.valueOf(GL45.glGetInteger(GL45.GL_MAX_FRAMEBUFFER_HEIGHT));
        }
        return null;
    }

    @Override
    public void close() {
        for (var glName : bufferGlNames.values()) {
            GL45.glDeleteBuffers(glName);
        }
        bufferGlNames.clear();
        log.info("GlRenderDevice closed");
    }

    private static int mapUsage(AccessPattern pattern) {
        if (pattern == AccessPattern.STATIC) return GL45.GL_STATIC_DRAW;
        if (pattern == AccessPattern.DYNAMIC) return GL45.GL_DYNAMIC_DRAW;
        if (pattern == AccessPattern.STREAM) return GL45.GL_STREAM_DRAW;
        return GL45.GL_STATIC_DRAW;
    }
}
