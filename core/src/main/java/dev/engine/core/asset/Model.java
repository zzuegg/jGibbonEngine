package dev.engine.core.asset;

import dev.engine.core.material.Material;
import dev.engine.core.math.Mat4;
import dev.engine.core.mesh.MeshData;

import java.util.List;

/**
 * A loaded 3D model with multiple meshes, materials, and hierarchy.
 * Loaded by Assimp (glTF, FBX, OBJ, etc).
 */
public record Model(
        List<SubMesh> meshes,
        List<Material> materials,
        List<Node> nodes
) {
    /** A mesh + material index pair. */
    public record SubMesh(MeshData meshData, int materialIndex) {}

    /** A node in the model's scene hierarchy. */
    public record Node(String name, Mat4 transform, List<Integer> meshIndices, List<Node> children) {}
}
