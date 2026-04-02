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

    public Transform withPosition(Vec3 position) { return new Transform(position, rotation, scale); }
    public Transform withRotation(Quat rotation) { return new Transform(position, rotation, scale); }
    public Transform withScale(Vec3 scale) { return new Transform(position, rotation, scale); }
    public Transform withScale(float uniform) { return withScale(new Vec3(uniform, uniform, uniform)); }

    /** Computes the 4x4 local transform matrix. */
    public Mat4 toMatrix() {
        return Mat4.translation(position)
                .mul(rotation.toMat4())
                .mul(Mat4.scaling(scale.x(), scale.y(), scale.z()));
    }
}
