package dev.engine.graphics.opengl;

import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.Scene;
import dev.engine.core.scene.camera.Camera;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.renderer.MeshRenderer;
import dev.engine.graphics.renderer.Renderable;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.graphics.window.WindowDescriptor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.opengl.GL45;

import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the full scene rendering pipeline produces correct pixels:
 * scene graph → transactions → mesh renderer → draw batch → GPU → pixel readback.
 */
class SceneRenderVerificationTest {

    static final String VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            layout(location = 1) in vec3 color;
            layout(row_major, std140, binding = 0) uniform Matrices { mat4 mvp; };
            out vec3 vColor;
            void main() {
                gl_Position = mvp * vec4(position, 1.0);
                vColor = color;
            }
            """;
    static final String FS = """
            #version 450 core
            in vec3 vColor;
            out vec4 fragColor;
            void main() {
                fragColor = vec4(vColor, 1.0);
            }
            """;

    record Vertex(float x, float y, float z, float r, float g, float b) {}

    static GlfwWindowToolkit toolkit;
    static GlRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("Verify", 128, 128));
        device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Test
    void sceneGraphRendersMultipleObjectsCorrectly() {
        // Setup scene
        var scene = new Scene();
        var meshRenderer = new MeshRenderer();
        var camera = new Camera();

        // Create cube mesh (fullscreen quads that are easy to verify)
        var layout = StructLayout.of(Vertex.class);
        var verts = new Vertex[]{
                new Vertex(-0.5f, -0.5f, 0f, 1f, 0f, 0f),
                new Vertex(0.5f, -0.5f, 0f, 1f, 0f, 0f),
                new Vertex(0.5f, 0.5f, 0f, 1f, 0f, 0f),
                new Vertex(-0.5f, 0.5f, 0f, 1f, 0f, 0f),
        };
        long vbSize = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++)
                layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }

        int[] indices = {0, 1, 2, 0, 2, 3};
        long ibSize = (long) indices.length * Integer.BYTES;
        var ibo = device.createBuffer(new BufferDescriptor(ibSize, BufferUsage.INDEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(ibo)) {
            for (int i = 0; i < indices.length; i++)
                w.segment().setAtIndex(ValueLayout.JAVA_INT, i, indices[i]);
        }

        var format = VertexFormat.of(
                new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
                new VertexAttribute(1, 3, ComponentType.FLOAT, false, 3 * Float.BYTES));
        var vertexInput = device.createVertexInput(format);
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));
        var matLayout = StructLayout.of(Mat4.class);
        var ubo = device.createBuffer(new BufferDescriptor(matLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));

        // Create entity and assign renderable
        var entity = scene.createEntity();
        scene.setLocalTransform(entity, Mat4.IDENTITY); // centered
        meshRenderer.processTransactions(dev.engine.core.scene.SceneAccess.drainTransactions(scene));
        meshRenderer.setRenderable(entity, new Renderable(vbo, ibo, vertexInput, pipeline, 4, 6));

        // Camera looking straight at the quad
        camera.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
        camera.lookAt(new Vec3(0f, 0f, 2f), Vec3.ZERO, Vec3.UNIT_Y);
        var vp = camera.viewProjectionMatrix();

        // Offscreen FBO
        int fbo = GL45.glCreateFramebuffers();
        int colorTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
        GL45.glTextureStorage2D(colorTex, 1, GL45.GL_RGBA8, 128, 128);
        GL45.glNamedFramebufferTexture(fbo, GL45.GL_COLOR_ATTACHMENT0, colorTex, 0);
        int depthTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
        GL45.glTextureStorage2D(depthTex, 1, GL45.GL_DEPTH_COMPONENT24, 128, 128);
        GL45.glNamedFramebufferTexture(fbo, GL45.GL_DEPTH_ATTACHMENT, depthTex, 0);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, fbo);

        // Render via mesh renderer batch
        device.beginFrame();
        var rec = new CommandRecorder();
        rec.viewport(0, 0, 128, 128);
        rec.setDepthTest(true);
        rec.clear(0f, 0f, 0f, 1f);
        rec.bindPipeline(pipeline);

        for (var cmd : meshRenderer.collectBatch()) {
            var mvp = vp.mul(cmd.transform());
            try (var writer = device.writeBuffer(ubo)) {
                matLayout.write(writer.segment(), 0, mvp);
            }
            rec.bindUniformBuffer(0, ubo);
            rec.bindVertexBuffer(cmd.renderable().vertexBuffer(), cmd.renderable().vertexInput());
            rec.bindIndexBuffer(cmd.renderable().indexBuffer());
            rec.drawIndexed(cmd.renderable().indexCount(), 0);
        }
        device.submit(rec.finish());
        device.endFrame();

        // Verify center pixel is red (the quad is centered and should cover the center)
        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GL45.glReadPixels(64, 64, 1, 1, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, pixel);
        int r = pixel.get(0) & 0xFF;
        int g = pixel.get(1) & 0xFF;
        int b = pixel.get(2) & 0xFF;
        assertTrue(r > 200, "Center pixel red should be high (quad visible), got " + r);
        assertTrue(g < 50, "Green should be low, got " + g);
        assertTrue(b < 50, "Blue should be low, got " + b);

        // Verify corner pixel is black (background, quad doesn't cover corners)
        ByteBuffer corner = ByteBuffer.allocateDirect(4);
        GL45.glReadPixels(0, 0, 1, 1, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, corner);
        int cr = corner.get(0) & 0xFF;
        assertTrue(cr < 50, "Corner should be background (black), got R=" + cr);

        // Cleanup
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
        GL45.glDeleteFramebuffers(fbo);
        GL45.glDeleteTextures(colorTex);
        GL45.glDeleteTextures(depthTex);
        device.destroyBuffer(ubo);
        device.destroyBuffer(ibo);
        device.destroyBuffer(vbo);
        device.destroyVertexInput(vertexInput);
        device.destroyPipeline(pipeline);
    }
}
