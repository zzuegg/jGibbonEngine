package dev.engine.graphics.common;

import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.mesh.MeshData;
import dev.engine.graphics.common.mesh.PrimitiveMeshes;
import dev.engine.graphics.pipeline.PipelineDescriptor;
import dev.engine.graphics.pipeline.ShaderSource;
import dev.engine.graphics.pipeline.ShaderStage;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;
import dev.engine.core.scene.component.Transform;
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
            renderer.scene().setMesh(entity.handle(), mesh);
            renderer.scene().setLocalTransform(entity, Mat4.translation(0, 0, 0));

            var cam = renderer.createCamera();
            cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
            cam.lookAt(new Vec3(0, 0, 5), Vec3.ZERO, Vec3.UNIT_Y);
            renderer.setActiveCamera(cam);

            assertDoesNotThrow(() -> renderer.renderFrame());
        }
    }

    @Nested
    class LazyShaderCompilation {
        @Test void constructorDoesNotEagerlyCompileShaders() {
            // Renderer should construct without attempting shader compilation
            assertDoesNotThrow(() -> Renderer.createHeadless());
        }

        @Test void renderFrameWithMaterialResolvesShaderLazily() {
            // Entity with PBR material — pipeline resolved lazily with material keys
            var entity = renderer.scene().createEntity();
            entity.add(PrimitiveMeshes.cube());
            entity.add(MaterialData.pbr(new Vec3(1, 0, 0), 0.5f, 0.0f));

            var cam = renderer.createCamera();
            cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
            cam.lookAt(new Vec3(0, 0, 5), Vec3.ZERO, Vec3.UNIT_Y);
            renderer.setActiveCamera(cam);

            assertDoesNotThrow(() -> renderer.renderFrame());
        }

        @Test void pipelineResolutionUsesShaderWithMaterialKeys() {
            // Different materials with different shader hints both resolve lazily
            var e1 = renderer.scene().createEntity();
            e1.add(PrimitiveMeshes.cube());
            e1.add(MaterialData.pbr(new Vec3(1, 0, 0), 0.5f, 0.0f));

            var e2 = renderer.scene().createEntity();
            e2.add(PrimitiveMeshes.cube());
            e2.add(MaterialData.unlit(new Vec3(0, 1, 0)));

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
