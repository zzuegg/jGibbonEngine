package dev.engine.graphics.common;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.graphics.vertex.ComponentType;
import dev.engine.graphics.vertex.VertexAttribute;
import dev.engine.graphics.vertex.VertexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RendererTest {

    // Stub backend for testing without GPU
    private Renderer renderer;

    @BeforeEach
    void setUp() {
        renderer = Renderer.createHeadless();
    }

    @Nested
    class SceneAccess {
        @Test void rendererOwnsScene() {
            assertNotNull(renderer.scene());
        }

        @Test void createEntityViaScene() {
            var entity = renderer.scene().createEntity();
            assertNotNull(entity);
        }
    }

    @Nested
    class CameraManagement {
        @Test void createCamera() {
            var cam = renderer.createCamera();
            assertNotNull(cam);
        }

        @Test void setActiveCamera() {
            var cam = renderer.createCamera();
            cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
            renderer.setActiveCamera(cam);
            assertSame(cam, renderer.activeCamera());
        }

        @Test void multipleCameras() {
            var cam1 = renderer.createCamera();
            var cam2 = renderer.createCamera();
            assertNotSame(cam1, cam2);
            renderer.setActiveCamera(cam2);
            assertSame(cam2, renderer.activeCamera());
        }
    }

    @Nested
    class MeshManagement {
        @Test void registerMeshFromVertexData() {
            var format = VertexFormat.of(
                    new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
            var mesh = renderer.createMesh(
                    new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0},
                    new int[]{0, 1, 2},
                    format);
            assertNotNull(mesh);
        }
    }

    @Nested
    class RenderLoop {
        @Test void renderFrameDoesNotThrowWithEmptyScene() {
            var cam = renderer.createCamera();
            cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
            cam.lookAt(new Vec3(0, 0, 5), Vec3.ZERO, Vec3.UNIT_Y);
            renderer.setActiveCamera(cam);
            assertDoesNotThrow(() -> renderer.renderFrame());
        }

        @Test void renderFrameWithEntity() {
            var format = VertexFormat.of(
                    new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0));
            var mesh = renderer.createMesh(
                    new float[]{0, 0, 0, 1, 0, 0, 0, 1, 0},
                    new int[]{0, 1, 2},
                    format);

            var entity = renderer.scene().createEntity();
            renderer.scene().setMesh(entity, mesh);
            renderer.scene().setLocalTransform(entity, Mat4.translation(0, 0, 0));

            var cam = renderer.createCamera();
            cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
            cam.lookAt(new Vec3(0, 0, 5), Vec3.ZERO, Vec3.UNIT_Y);
            renderer.setActiveCamera(cam);

            assertDoesNotThrow(() -> renderer.renderFrame());
        }
    }

    @Nested
    class Capabilities {
        @Test void queryBackendName() {
            assertNotNull(renderer.backendName());
        }
    }
}
