package dev.engine.core.scene.camera;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;

public class Camera {

    private Mat4 projection = Mat4.IDENTITY;
    private Mat4 view = Mat4.IDENTITY;

    public void setPerspective(float fovY, float aspect, float near, float far) {
        this.projection = Mat4.perspective(fovY, aspect, near, far);
    }

    public void setOrthographic(float left, float right, float bottom, float top, float near, float far) {
        this.projection = ortho(left, right, bottom, top, near, far);
    }

    public void lookAt(Vec3 eye, Vec3 center, Vec3 up) {
        this.view = Mat4.lookAt(eye, center, up);
    }

    public Mat4 projectionMatrix() { return projection; }
    public Mat4 viewMatrix() { return view; }
    public Mat4 viewProjectionMatrix() { return projection.mul(view); }

    private static Mat4 ortho(float l, float r, float b, float t, float n, float f) {
        return new Mat4(
                2f / (r - l), 0, 0, -(r + l) / (r - l),
                0, 2f / (t - b), 0, -(t + b) / (t - b),
                0, 0, -2f / (f - n), -(f + n) / (f - n),
                0, 0, 0, 1
        );
    }
}
