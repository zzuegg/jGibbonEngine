package dev.engine.graphics.opengl;

import dev.engine.windowing.glfw.GlfwWindowToolkit;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderCompilationException;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlPipelineTest {

    static final String VERTEX_SHADER = """
            #version 450 core
            layout(location = 0) in vec3 position;
            void main() {
                gl_Position = vec4(position, 1.0);
            }
            """;

    static final String FRAGMENT_SHADER = """
            #version 450 core
            out vec4 fragColor;
            void main() {
                fragColor = vec4(1.0, 0.0, 0.0, 1.0);
            }
            """;

    static final String INVALID_SHADER = """
            #version 450 core
            this is not valid GLSL;
            """;

    static GlfwWindowToolkit toolkit;
    static GlRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit(GlfwWindowToolkit.OPENGL_HINTS);
        var window = toolkit.createWindow(new WindowDescriptor("GPU Test", 1, 1));
        device = new GlRenderDevice(window, new dev.engine.providers.lwjgl.graphics.opengl.LwjglGlBindings());
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Nested
    class PipelineCreation {
        @Test
        void createPipelineReturnsValidHandle() {
            var desc = PipelineDescriptor.of(
                    new ShaderSource(ShaderStage.VERTEX, VERTEX_SHADER),
                    new ShaderSource(ShaderStage.FRAGMENT, FRAGMENT_SHADER)
            );
            var handle = device.createPipeline(desc);
            assertNotEquals(Handle.invalid(), handle);
            assertTrue(device.isValidPipeline(handle));
        }

        @Test
        void destroyPipelineInvalidatesHandle() {
            var desc = PipelineDescriptor.of(
                    new ShaderSource(ShaderStage.VERTEX, VERTEX_SHADER),
                    new ShaderSource(ShaderStage.FRAGMENT, FRAGMENT_SHADER)
            );
            var handle = device.createPipeline(desc);
            device.destroyPipeline(handle);
            assertFalse(device.isValidPipeline(handle));
        }
    }

    @Nested
    class CompilationErrors {
        @Test
        void invalidShaderThrowsCompilationException() {
            var desc = PipelineDescriptor.of(
                    new ShaderSource(ShaderStage.VERTEX, INVALID_SHADER),
                    new ShaderSource(ShaderStage.FRAGMENT, FRAGMENT_SHADER)
            );
            assertThrows(ShaderCompilationException.class, () -> device.createPipeline(desc));
        }
    }
}
