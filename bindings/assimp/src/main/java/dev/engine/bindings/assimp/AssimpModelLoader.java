package dev.engine.bindings.assimp;

import dev.engine.core.asset.AssetLoader;
import dev.engine.core.asset.AssetSource;
import dev.engine.core.asset.Model;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec3;
import dev.engine.core.mesh.ComponentType;
import dev.engine.core.mesh.MeshData;
import dev.engine.core.mesh.VertexAttribute;
import dev.engine.core.mesh.VertexFormat;

import org.lwjgl.assimp.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.lwjgl.assimp.Assimp.*;

/**
 * Loads 3D models via LWJGL's Assimp binding.
 * Supports glTF, GLB, FBX, OBJ, Blender, 3DS, and Collada files.
 */
public class AssimpModelLoader implements AssetLoader<Model> {

    private static final Set<String> EXTENSIONS = Set.of(
            ".gltf", ".glb", ".fbx", ".obj", ".blend", ".3ds", ".dae"
    );

    /** Standard vertex format: position(3f) + normal(3f) + texcoord(2f) = 32 bytes stride. */
    public static final VertexFormat STANDARD_FORMAT = VertexFormat.of(
            new VertexAttribute(0, 3, ComponentType.FLOAT, false, 0),
            new VertexAttribute(1, 3, ComponentType.FLOAT, false, 12),
            new VertexAttribute(2, 2, ComponentType.FLOAT, false, 24)
    );

    private static final int IMPORT_FLAGS =
            aiProcess_Triangulate |
            aiProcess_GenNormals |
            aiProcess_CalcTangentSpace |
            aiProcess_FlipUVs;

    @Override
    public boolean supports(String path) {
        var lower = path.toLowerCase();
        return EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    @Override
    public Class<Model> assetType() {
        return Model.class;
    }

    @Override
    public Model load(AssetSource.AssetData data) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(data.bytes().length)
                .order(ByteOrder.nativeOrder());
        buffer.put(data.bytes());
        buffer.flip();

        // Hint the format from the file extension
        String hint = "";
        int dotIndex = data.path().lastIndexOf('.');
        if (dotIndex >= 0) {
            hint = data.path().substring(dotIndex + 1);
        }

        AIScene scene = aiImportFileFromMemory(buffer, IMPORT_FLAGS, hint);
        if (scene == null) {
            throw new RuntimeException("Assimp failed to load: " + data.path()
                    + " — " + aiGetErrorString());
        }

        try {
            List<Model.SubMesh> meshes = extractMeshes(scene);
            List<MaterialData> materials = extractMaterials(scene);
            List<Model.Node> nodes = List.of(extractNode(scene.mRootNode()));
            return new Model(meshes, materials, nodes);
        } finally {
            aiReleaseImport(scene);
        }
    }

    private List<Model.SubMesh> extractMeshes(AIScene scene) {
        int meshCount = scene.mNumMeshes();
        var meshes = new ArrayList<Model.SubMesh>(meshCount);

        for (int i = 0; i < meshCount; i++) {
            AIMesh aiMesh = AIMesh.create(scene.mMeshes().get(i));
            int vertexCount = aiMesh.mNumVertices();

            // 8 floats per vertex: pos(3) + normal(3) + uv(2)
            float[] vertices = new float[vertexCount * 8];

            AIVector3D.Buffer positions = aiMesh.mVertices();
            AIVector3D.Buffer normals = aiMesh.mNormals();
            AIVector3D.Buffer texCoords = aiMesh.mTextureCoords(0);

            for (int v = 0; v < vertexCount; v++) {
                int base = v * 8;

                AIVector3D pos = positions.get(v);
                vertices[base]     = pos.x();
                vertices[base + 1] = pos.y();
                vertices[base + 2] = pos.z();

                if (normals != null) {
                    AIVector3D n = normals.get(v);
                    vertices[base + 3] = n.x();
                    vertices[base + 4] = n.y();
                    vertices[base + 5] = n.z();
                }

                if (texCoords != null) {
                    AIVector3D tc = texCoords.get(v);
                    vertices[base + 6] = tc.x();
                    vertices[base + 7] = tc.y();
                }
            }

            // Extract indices
            int faceCount = aiMesh.mNumFaces();
            AIFace.Buffer faces = aiMesh.mFaces();
            var indexList = new ArrayList<Integer>();
            for (int f = 0; f < faceCount; f++) {
                AIFace face = faces.get(f);
                var idxBuf = face.mIndices();
                for (int j = 0; j < face.mNumIndices(); j++) {
                    indexList.add(idxBuf.get(j));
                }
            }
            int[] indices = indexList.stream().mapToInt(Integer::intValue).toArray();

            MeshData meshData = MeshData.create(vertices, indices, STANDARD_FORMAT);
            meshes.add(new Model.SubMesh(meshData, aiMesh.mMaterialIndex()));
        }

        return meshes;
    }

    private List<MaterialData> extractMaterials(AIScene scene) {
        int matCount = scene.mNumMaterials();
        var materials = new ArrayList<MaterialData>(matCount);

        for (int i = 0; i < matCount; i++) {
            AIMaterial aiMat = AIMaterial.create(scene.mMaterials().get(i));
            var mat = MaterialData.create("PBR");

            // Diffuse color
            AIColor4D color = AIColor4D.create();
            if (aiGetMaterialColor(aiMat, AI_MATKEY_COLOR_DIFFUSE, aiTextureType_NONE, 0, color) == aiReturn_SUCCESS) {
                mat = mat.set(MaterialData.ALBEDO_COLOR, new Vec3(color.r(), color.g(), color.b()))
                         .set(MaterialData.OPACITY, color.a());
            }

            // Specular color — use specular intensity to approximate metallic
            AIColor4D specColor = AIColor4D.create();
            if (aiGetMaterialColor(aiMat, AI_MATKEY_COLOR_SPECULAR, aiTextureType_NONE, 0, specColor) == aiReturn_SUCCESS) {
                float specIntensity = (specColor.r() + specColor.g() + specColor.b()) / 3.0f;
                mat = mat.set(MaterialData.METALLIC, specIntensity);
            }

            // Roughness (Assimp stores shininess; convert)
            float[] shininess = new float[1];
            int[] pMax = new int[]{1};
            if (aiGetMaterialFloatArray(aiMat, AI_MATKEY_SHININESS, aiTextureType_NONE, 0, shininess, pMax) == aiReturn_SUCCESS) {
                float roughness = (float) Math.sqrt(2.0 / (shininess[0] + 2.0));
                mat = mat.set(MaterialData.ROUGHNESS, Math.min(1.0f, Math.max(0.0f, roughness)));
            } else {
                mat = mat.set(MaterialData.ROUGHNESS, 0.5f);
            }

            materials.add(mat);
        }

        return materials;
    }

    private Model.Node extractNode(AINode aiNode) {
        String name = aiNode.mName().dataString();

        // Read 4x4 transform matrix (Assimp stores row-major)
        AIMatrix4x4 m = aiNode.mTransformation();
        Mat4 transform = new Mat4(
                m.a1(), m.a2(), m.a3(), m.a4(),
                m.b1(), m.b2(), m.b3(), m.b4(),
                m.c1(), m.c2(), m.c3(), m.c4(),
                m.d1(), m.d2(), m.d3(), m.d4()
        );

        // Collect mesh indices
        int numMeshes = aiNode.mNumMeshes();
        var meshIndices = new ArrayList<Integer>(numMeshes);
        if (numMeshes > 0) {
            var meshBuf = aiNode.mMeshes();
            for (int i = 0; i < numMeshes; i++) {
                meshIndices.add(meshBuf.get(i));
            }
        }

        // Recurse children
        int numChildren = aiNode.mNumChildren();
        var children = new ArrayList<Model.Node>(numChildren);
        if (numChildren > 0) {
            var childBuf = aiNode.mChildren();
            for (int i = 0; i < numChildren; i++) {
                AINode child = AINode.create(childBuf.get(i));
                children.add(extractNode(child));
            }
        }

        return new Model.Node(name, transform, meshIndices, children);
    }
}
