package dev.engine.core.scene.camera;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;

public class Camera {

    private Mat4 projection = Mat4.IDENTITY;
    private Mat4 view = Mat4.IDENTITY;
    private Vec3 position = Vec3.ZERO;
    private float nearPlane;
    private float farPlane;

    public Camera() {
        this(0.1f, 1000f);
    }

    public Camera(float nearPlane, float farPlane) {
        this.nearPlane = nearPlane;
        this.farPlane = farPlane;
    }

    public void setPerspective(float fovY, float aspect, float near, float far) {
        this.projection = Mat4.perspective(fovY, aspect, near, far);
        this.nearPlane = near;
        this.farPlane = far;
    }

    public void setOrthographic(float left, float right, float bottom, float top, float near, float far) {
        this.projection = Mat4.ortho(left, right, bottom, top, near, far);
        this.nearPlane = near;
        this.farPlane = far;
    }

    public void lookAt(Vec3 eye, Vec3 center, Vec3 up) {
        this.view = Mat4.lookAt(eye, center, up);
        this.position = eye;
    }

    public Mat4 projectionMatrix() { return projection; }
    public Mat4 viewMatrix() { return view; }
    public Mat4 viewProjectionMatrix() { return projection.mul(view); }
    public Vec3 position() { return position; }
    public float nearPlane() { return nearPlane; }
    public float farPlane() { return farPlane; }
}
