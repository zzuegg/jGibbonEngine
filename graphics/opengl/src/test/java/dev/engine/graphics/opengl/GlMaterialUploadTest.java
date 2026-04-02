package dev.engine.graphics.opengl;

import dev.engine.core.handle.Handle;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import dev.engine.core.scene.Scene;
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
 * GPU-verified tests that material property changes flow through
 * Scene → Transaction → MeshRenderer → DrawCommand → GPU → pixel output.
 */
class GlMaterialUploadTest {

    // Shader reads material color from UBO binding 1
    static final String VS = """
            #version 450 core
            layout(location = 0) in vec3 position;
            layout(row_major, std140, binding = 0) uniform Matrices { mat4 mvp; };
            void main() { gl_Position = mvp * vec4(position, 1.0); }
            """;

    // Fragment shader reads albedo color from material UBO at binding 1
    static final String FS = """
            #version 450 core
            layout(std140, binding = 1) uniform MaterialData {
                vec3 albedoColor;
                float roughness;
            };
            out vec4 fragColor;
            void main() {
                fragColor = vec4(albedoColor, 1.0);
            }
            """;

    static final PropertyKey<Vec3> ALBEDO = PropertyKey.of("albedoColor", Vec3.class);
    static final PropertyKey<Float> ROUGHNESS = PropertyKey.of("roughness", Float.class);

    record Vertex(float x, float y, float z) {}

    static GlfwWindowToolkit toolkit;
    static GlRenderDevice device;

    @BeforeAll
    static void setUp() {
        toolkit = new GlfwWindowToolkit();
        var window = toolkit.createWindow(new WindowDescriptor("GPU Test", 1, 1));
        device = new GlRenderDevice((GlfwWindowToolkit.GlfwWindowHandle) window);
    }

    @AfterAll
    static void tearDown() {
        if (device != null) device.close();
        if (toolkit != null) toolkit.close();
    }

    @Test
    void materialColorReachesFragmentShader() {
        var result = renderWithMaterialColor(new Vec3(0f, 1f, 0f)); // green
        int g = result[1];
        assertTrue(g > 200, "Green should be high from material UBO, got " + g);
        assertTrue(result[0] < 50, "Red should be low, got " + result[0]);
    }

    @Test
    void changingMaterialColorChangesPixelOutput() {
        // First render: red
        var red = renderWithMaterialColor(new Vec3(1f, 0f, 0f));
        assertTrue(red[0] > 200, "Red should be high, got " + red[0]);
        assertTrue(red[1] < 50, "Green should be low, got " + red[1]);

        // Second render: blue — same pipeline, same geometry, different material
        var blue = renderWithMaterialColor(new Vec3(0f, 0f, 1f));
        assertTrue(blue[2] > 200, "Blue should be high, got " + blue[2]);
        assertTrue(blue[0] < 50, "Red should be low, got " + blue[0]);
    }

    @Test
    void materialChangeThroughTransactionAffectsRendering() {
        // Setup scene + renderer
        var scene = new Scene();
        var meshRenderer = new MeshRenderer();
        var entity = scene.createEntity();
        meshRenderer.processTransactions(dev.engine.core.scene.SceneAccess.drainTransactions(scene));

        // Set initial material: red
        scene.setMaterialProperty(entity, ALBEDO, new Vec3(1f, 0f, 0f));
        scene.setMaterialProperty(entity, ROUGHNESS, 0.5f);
        meshRenderer.processTransactions(dev.engine.core.scene.SceneAccess.drainTransactions(scene));

        // Verify MeshRenderer received the material data
        var mat = meshRenderer.getMaterial(entity.handle());
        assertNotNull(mat);
        assertEquals(new Vec3(1f, 0f, 0f), mat.get(ALBEDO));

        // Render with red material
        var redPixel = renderEntityWithMaterial(meshRenderer, entity.handle());
        assertTrue(redPixel[0] > 200, "Red should be high, got " + redPixel[0]);

        // Change material to green through transaction
        scene.setMaterialProperty(entity, ALBEDO, new Vec3(0f, 1f, 0f));
        meshRenderer.processTransactions(dev.engine.core.scene.SceneAccess.drainTransactions(scene));

        // Verify MeshRenderer updated
        assertEquals(new Vec3(0f, 1f, 0f), meshRenderer.getMaterial(entity.handle()).get(ALBEDO));

        // Render with green material
        var greenPixel = renderEntityWithMaterial(meshRenderer, entity.handle());
        assertTrue(greenPixel[1] > 200, "Green should be high after change, got " + greenPixel[1]);
        assertTrue(greenPixel[0] < 50, "Red should be low after change, got " + greenPixel[0]);
    }

    @Test
    void materialReplaceThroughTransactionAffectsRendering() {
        var scene = new Scene();
        var meshRenderer = new MeshRenderer();
        var entity = scene.createEntity();
        meshRenderer.processTransactions(dev.engine.core.scene.SceneAccess.drainTransactions(scene));

        // Set initial material via replace (full property map)
        var props = dev.engine.core.property.PropertyMap.builder()
                .set(ALBEDO, new Vec3(0f, 0f, 1f))
                .set(ROUGHNESS, 0.3f)
                .build();
        scene.setMaterialProperties(entity, props);
        meshRenderer.processTransactions(dev.engine.core.scene.SceneAccess.drainTransactions(scene));

        var bluePixel = renderEntityWithMaterial(meshRenderer, entity.handle());
        assertTrue(bluePixel[2] > 200, "Blue should be high, got " + bluePixel[2]);

        // Replace with yellow
        var yellowProps = dev.engine.core.property.PropertyMap.builder()
                .set(ALBEDO, new Vec3(1f, 1f, 0f))
                .set(ROUGHNESS, 0.7f)
                .build();
        scene.setMaterialProperties(entity, yellowProps);
        meshRenderer.processTransactions(dev.engine.core.scene.SceneAccess.drainTransactions(scene));

        var yellowPixel = renderEntityWithMaterial(meshRenderer, entity.handle());
        assertTrue(yellowPixel[0] > 200, "Red should be high (yellow), got " + yellowPixel[0]);
        assertTrue(yellowPixel[1] > 200, "Green should be high (yellow), got " + yellowPixel[1]);
        assertTrue(yellowPixel[2] < 50, "Blue should be low (yellow), got " + yellowPixel[2]);
    }

    // --- Helpers ---

    private int[] renderWithMaterialColor(Vec3 color) {
        var layout = StructLayout.of(Vertex.class);
        var verts = new Vertex[]{
                new Vertex(-1f, -1f, 0f), new Vertex(3f, -1f, 0f), new Vertex(-1f, 3f, 0f),
        };
        long vbSize = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++)
                layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }

        var matLayout = StructLayout.of(Mat4.class);
        var ubo = device.createBuffer(new BufferDescriptor(matLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        try (var w = device.writeBuffer(ubo)) { matLayout.write(w.segment(), 0, Mat4.IDENTITY); }

        // Material UBO: vec3 albedoColor + float roughness (std140: vec3 padded to 16 bytes)
        var matUbo = device.createBuffer(new BufferDescriptor(32, BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        try (var w = device.writeBuffer(matUbo)) {
            w.segment().set(ValueLayout.JAVA_FLOAT, 0, color.x());
            w.segment().set(ValueLayout.JAVA_FLOAT, 4, color.y());
            w.segment().set(ValueLayout.JAVA_FLOAT, 8, color.z());
            w.segment().set(ValueLayout.JAVA_FLOAT, 16, 0.5f); // roughness at offset 16 (std140 padding)
        }

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        var vertexInput = device.createVertexInput(format);
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));

        int fbo = GL45.glCreateFramebuffers();
        int colorTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
        GL45.glTextureStorage2D(colorTex, 1, GL45.GL_RGBA8, 64, 64);
        GL45.glNamedFramebufferTexture(fbo, GL45.GL_COLOR_ATTACHMENT0, colorTex, 0);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, fbo);

        device.beginFrame();
        var rec = new CommandRecorder();
        rec.viewport(0, 0, 64, 64);
        rec.clear(0f, 0f, 0f, 1f);
        rec.bindPipeline(pipeline);
        rec.bindUniformBuffer(0, ubo);
        rec.bindUniformBuffer(1, matUbo);
        rec.bindVertexBuffer(vbo, vertexInput);
        rec.draw(3, 0);
        device.submit(rec.finish());
        device.endFrame();

        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GL45.glReadPixels(32, 32, 1, 1, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, pixel);
        int[] result = {pixel.get(0) & 0xFF, pixel.get(1) & 0xFF, pixel.get(2) & 0xFF, pixel.get(3) & 0xFF};

        GL45.glDeleteFramebuffers(fbo);
        GL45.glDeleteTextures(colorTex);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
        device.destroyPipeline(pipeline);
        device.destroyVertexInput(vertexInput);
        device.destroyBuffer(matUbo);
        device.destroyBuffer(ubo);
        device.destroyBuffer(vbo);

        return result;
    }

    private int[] renderEntityWithMaterial(MeshRenderer meshRenderer, dev.engine.core.handle.Handle<?> entity) {
        var layout = StructLayout.of(Vertex.class);
        var verts = new Vertex[]{
                new Vertex(-1f, -1f, 0f), new Vertex(3f, -1f, 0f), new Vertex(-1f, 3f, 0f),
        };
        long vbSize = (long) layout.size() * verts.length;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < verts.length; i++)
                layout.write(w.segment(), (long) layout.size() * i, verts[i]);
        }

        var format = VertexFormat.of(new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
        var vertexInput = device.createVertexInput(format);
        var pipeline = device.createPipeline(PipelineDescriptor.of(
                new ShaderSource(ShaderStage.VERTEX, VS),
                new ShaderSource(ShaderStage.FRAGMENT, FS)));

        meshRenderer.setRenderable(entity, new Renderable(vbo, null, vertexInput, pipeline, 3, 0));

        var matLayout = StructLayout.of(Mat4.class);
        var mvpUbo = device.createBuffer(new BufferDescriptor(matLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        try (var w = device.writeBuffer(mvpUbo)) { matLayout.write(w.segment(), 0, Mat4.IDENTITY); }

        // Create material UBO from the MeshRenderer's tracked material data
        var matData = meshRenderer.getMaterial(entity);
        var matUbo = device.createBuffer(new BufferDescriptor(32, BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        try (var w = device.writeBuffer(matUbo)) {
            Vec3 albedo = matData.get(ALBEDO);
            Float roughness = matData.get(ROUGHNESS);
            if (albedo != null) {
                w.segment().set(ValueLayout.JAVA_FLOAT, 0, albedo.x());
                w.segment().set(ValueLayout.JAVA_FLOAT, 4, albedo.y());
                w.segment().set(ValueLayout.JAVA_FLOAT, 8, albedo.z());
            }
            if (roughness != null) {
                w.segment().set(ValueLayout.JAVA_FLOAT, 16, roughness);
            }
        }

        int fbo = GL45.glCreateFramebuffers();
        int colorTex = GL45.glCreateTextures(GL45.GL_TEXTURE_2D);
        GL45.glTextureStorage2D(colorTex, 1, GL45.GL_RGBA8, 64, 64);
        GL45.glNamedFramebufferTexture(fbo, GL45.GL_COLOR_ATTACHMENT0, colorTex, 0);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, fbo);

        device.beginFrame();
        var rec = new CommandRecorder();
        rec.viewport(0, 0, 64, 64);
        rec.clear(0f, 0f, 0f, 1f);
        rec.bindPipeline(pipeline);
        rec.bindUniformBuffer(0, mvpUbo);
        rec.bindUniformBuffer(1, matUbo);
        rec.bindVertexBuffer(vbo, vertexInput);
        rec.draw(3, 0);
        device.submit(rec.finish());
        device.endFrame();

        ByteBuffer pixel = ByteBuffer.allocateDirect(4);
        GL45.glReadPixels(32, 32, 1, 1, GL45.GL_RGBA, GL45.GL_UNSIGNED_BYTE, pixel);
        int[] result = {pixel.get(0) & 0xFF, pixel.get(1) & 0xFF, pixel.get(2) & 0xFF, pixel.get(3) & 0xFF};

        GL45.glDeleteFramebuffers(fbo);
        GL45.glDeleteTextures(colorTex);
        GL45.glBindFramebuffer(GL45.GL_FRAMEBUFFER, 0);
        device.destroyPipeline(pipeline);
        device.destroyVertexInput(vertexInput);
        device.destroyBuffer(matUbo);
        device.destroyBuffer(mvpUbo);
        device.destroyBuffer(vbo);

        return result;
    }
}
