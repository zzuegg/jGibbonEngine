package dev.engine.graphics.common;

import dev.engine.core.handle.Handle;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.core.scene.Scene;
import dev.engine.core.scene.EntityTag;
import dev.engine.core.scene.camera.Camera;
import dev.engine.graphics.*;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.renderer.DrawCommand;
import dev.engine.graphics.renderer.MeshRenderer;
import dev.engine.graphics.renderer.Renderable;
import dev.engine.graphics.vertex.VertexFormat;

import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single public entry point for all rendering operations.
 *
 * <p>Users create entities in the scene, assign meshes and transforms,
 * set up a camera, and call {@link #renderFrame()}. The renderer handles
 * everything else: command recording, UBO management, batching, submission.
 */
public class Renderer implements AutoCloseable {

    private final RenderDevice device;
    private final Scene scene;
    private final MeshRenderer meshRenderer;
    private final List<Camera> cameras = new ArrayList<>();
    private Camera activeCamera;

    // Internal GPU resources
    private final Handle<BufferResource> mvpUbo;
    private final StructLayout mat4Layout = StructLayout.of(Mat4.class);
    private Handle<PipelineResource> defaultPipeline;

    // Entity → MeshHandle mapping
    private final Map<Handle<EntityTag>, MeshHandle> entityMeshes = new HashMap<>();

    // Viewport
    private int viewportWidth = 800;
    private int viewportHeight = 600;

    public Renderer(RenderDevice device) {
        this.device = device;
        this.scene = new Scene();
        this.meshRenderer = new MeshRenderer();
        this.mvpUbo = device.createBuffer(
                new BufferDescriptor(mat4Layout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
    }

    /**
     * Creates a headless renderer with a stub backend for testing.
     */
    public static Renderer createHeadless() {
        return new Renderer(new HeadlessRenderDevice());
    }

    // --- Scene access ---

    public Scene scene() { return scene; }

    // --- Camera management ---

    public Camera createCamera() {
        var cam = new Camera();
        cameras.add(cam);
        if (activeCamera == null) activeCamera = cam;
        return cam;
    }

    public void setActiveCamera(Camera camera) { this.activeCamera = camera; }
    public Camera activeCamera() { return activeCamera; }

    // --- Mesh management ---

    public MeshHandle createMesh(float[] vertices, int[] indices, VertexFormat format) {
        // Upload vertex data
        long vbSize = (long) vertices.length * Float.BYTES;
        var vbo = device.createBuffer(new BufferDescriptor(vbSize, BufferUsage.VERTEX, AccessPattern.STATIC));
        try (var w = device.writeBuffer(vbo)) {
            for (int i = 0; i < vertices.length; i++) {
                w.segment().setAtIndex(ValueLayout.JAVA_FLOAT, i, vertices[i]);
            }
        }

        // Upload index data
        Handle<BufferResource> ibo = null;
        int indexCount = 0;
        if (indices != null && indices.length > 0) {
            long ibSize = (long) indices.length * Integer.BYTES;
            ibo = device.createBuffer(new BufferDescriptor(ibSize, BufferUsage.INDEX, AccessPattern.STATIC));
            try (var w = device.writeBuffer(ibo)) {
                for (int i = 0; i < indices.length; i++) {
                    w.segment().setAtIndex(ValueLayout.JAVA_INT, i, indices[i]);
                }
            }
            indexCount = indices.length;
        }

        // Create vertex input
        var vertexInput = device.createVertexInput(format);

        int vertexCount = vertices.length / (format.stride() / Float.BYTES);
        return new MeshHandle(vbo, ibo, vertexInput, format, vertexCount, indexCount);
    }

    public void setMesh(Handle<EntityTag> entity, MeshHandle mesh) {
        entityMeshes.put(entity, mesh);
    }

    // --- Pipeline ---

    public void setDefaultPipeline(Handle<PipelineResource> pipeline) {
        this.defaultPipeline = pipeline;
    }

    public Handle<PipelineResource> createPipeline(PipelineDescriptor descriptor) {
        return device.createPipeline(descriptor);
    }

    // --- Viewport ---

    public void setViewport(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
    }

    // --- Render ---

    public void renderFrame() {
        // Process scene transactions
        meshRenderer.processTransactions(scene.drainTransactions());

        // Sync entity meshes → renderables
        for (var entry : entityMeshes.entrySet()) {
            var entity = entry.getKey();
            var mesh = entry.getValue();
            if (meshRenderer.hasEntity(entity) && meshRenderer.getRenderable(entity) == null) {
                meshRenderer.setRenderable(entity, new Renderable(
                        mesh.vertexBuffer(), mesh.indexBuffer(), mesh.vertexInput(),
                        defaultPipeline, mesh.vertexCount(), mesh.indexCount()));
            }
        }

        device.beginFrame();

        // Setup pass
        var setup = new CommandRecorder();
        setup.viewport(0, 0, viewportWidth, viewportHeight);
        setup.setDepthTest(true);
        setup.setCullFace(true);
        setup.clear(0.05f, 0.05f, 0.08f, 1f);
        if (defaultPipeline != null) {
            setup.bindPipeline(defaultPipeline);
        }
        device.submit(setup.finish());

        // Draw each object
        if (activeCamera != null && defaultPipeline != null) {
            var vp = activeCamera.viewProjectionMatrix();
            for (var cmd : meshRenderer.collectBatch()) {
                var mvp = vp.mul(cmd.transform());
                try (var w = device.writeBuffer(mvpUbo)) {
                    mat4Layout.write(w.segment(), 0, mvp.transpose());
                }
                var draw = new CommandRecorder();
                draw.bindUniformBuffer(0, mvpUbo);
                draw.bindVertexBuffer(cmd.renderable().vertexBuffer(), cmd.renderable().vertexInput());
                if (cmd.renderable().indexBuffer() != null) {
                    draw.bindIndexBuffer(cmd.renderable().indexBuffer());
                    draw.drawIndexed(cmd.renderable().indexCount(), 0);
                } else {
                    draw.draw(cmd.renderable().vertexCount(), 0);
                }
                device.submit(draw.finish());
            }
        }

        device.endFrame();
    }

    // --- Capabilities ---

    public <T> T queryCapability(DeviceCapability<T> capability) {
        return device.queryCapability(capability);
    }

    public boolean supports(DeviceCapability<Boolean> feature) {
        Boolean result = device.queryCapability(feature);
        return result != null && result;
    }

    public String backendName() {
        return queryCapability(DeviceCapability.BACKEND_NAME);
    }

    // --- Low-level access (escape hatch) ---

    public RenderDevice device() { return device; }

    @Override
    public void close() {
        device.destroyBuffer(mvpUbo);
        if (defaultPipeline != null) device.destroyPipeline(defaultPipeline);
        device.close();
    }
}
