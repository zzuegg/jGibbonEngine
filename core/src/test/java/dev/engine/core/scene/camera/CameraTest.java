package dev.engine.core.scene.camera;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CameraTest {

    static final float EPSILON = 1e-5f;

    @Nested
    class PerspectiveCamera {
        @Test void producesProjectionMatrix() {
            var cam = new Camera();
            cam.setPerspective((float) Math.toRadians(60), 16f / 9f, 0.1f, 100f);
            var proj = cam.projectionMatrix();
            assertNotNull(proj);
            assertNotEquals(Mat4.IDENTITY, proj);
        }

        @Test void lookAtProducesViewMatrix() {
            var cam = new Camera();
            cam.lookAt(new Vec3(0f, 0f, 5f), Vec3.ZERO, Vec3.UNIT_Y);
            var view = cam.viewMatrix();
            assertNotNull(view);
            assertNotEquals(Mat4.IDENTITY, view);
        }

        @Test void viewProjectionCombines() {
            var cam = new Camera();
            cam.setPerspective((float) Math.toRadians(60), 1f, 0.1f, 100f);
            cam.lookAt(new Vec3(0f, 0f, 5f), Vec3.ZERO, Vec3.UNIT_Y);
            var vp = cam.viewProjectionMatrix();
            assertEquals(cam.projectionMatrix().mul(cam.viewMatrix()), vp);
        }
    }

    @Nested
    class OrthographicCamera {
        @Test void producesOrthoProjection() {
            var cam = new Camera();
            cam.setOrthographic(-10f, 10f, -10f, 10f, 0.1f, 100f);
            var proj = cam.projectionMatrix();
            assertNotNull(proj);
        }
    }
}
