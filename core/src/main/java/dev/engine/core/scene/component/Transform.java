package dev.engine.core.scene.component;

import dev.engine.core.math.Mat4;
import dev.engine.core.math.Quat;
import dev.engine.core.math.Vec3;
import dev.engine.core.scene.Component;

/**
 * Spatial transform: position, rotation, scale.
 * Not every entity needs one — a timer or audio source might not.
 */
public record Transform(Vec3 position, Quat rotation, Vec3 scale) implements Component {

    public static final Transform IDENTITY = new Transform(Vec3.ZERO, Quat.IDENTITY, Vec3.ONE);

    public static Transform at(Vec3 position) { return new Transform(position, Quat.IDENTITY, Vec3.ONE); }
    public static Transform at(float x, float y, float z) { return at(new Vec3(x, y, z)); }

    // --- Immutable setters (return new Transform) ---

    public Transform withPosition(Vec3 position) { return new Transform(position, rotation, scale); }
    public Transform withPosition(float x, float y, float z) { return withPosition(new Vec3(x, y, z)); }
    public Transform withRotation(Quat rotation) { return new Transform(position, rotation, scale); }
    public Transform withScale(Vec3 scale) { return new Transform(position, rotation, scale); }
    public Transform withScale(float uniform) { return withScale(new Vec3(uniform, uniform, uniform)); }

    // --- Relative operations (return new Transform) ---

    /** Translates by offset (adds to current position). */
    public Transform moved(Vec3 offset) { return withPosition(position.add(offset)); }
    public Transform moved(float dx, float dy, float dz) { return moved(new Vec3(dx, dy, dz)); }

    /** Rotates incrementally around an axis. */
    public Transform rotated(Vec3 axis, float radians) {
        return withRotation(Quat.fromAxisAngle(axis, radians).mul(rotation));
    }

    public Transform rotatedX(float radians) { return rotated(Vec3.UNIT_X, radians); }
    public Transform rotatedY(float radians) { return rotated(Vec3.UNIT_Y, radians); }
    public Transform rotatedZ(float radians) { return rotated(Vec3.UNIT_Z, radians); }

    /** Multiplies current scale. */
    public Transform scaledBy(float factor) {
        return withScale(new Vec3(scale.x() * factor, scale.y() * factor, scale.z() * factor));
    }

    /** Orients to look at a target from current position. */
    public Transform lookingAt(Vec3 target, Vec3 up) {
        var dir = target.sub(position).normalize();
        var forward = new Vec3(0, 0, -1);
        var dot = forward.dot(dir);
        if (dot < -0.9999f) {
            return withRotation(Quat.fromAxisAngle(up, (float) Math.PI));
        }
        var cross = forward.cross(dir);
        return withRotation(new Quat(cross.x(), cross.y(), cross.z(), 1 + dot).normalize());
    }

    /** Computes the 4x4 local transform matrix. */
    public Mat4 toMatrix() {
        return Mat4.translation(position)
                .mul(rotation.toMat4())
                .mul(Mat4.scaling(scale.x(), scale.y(), scale.z()));
    }
}
