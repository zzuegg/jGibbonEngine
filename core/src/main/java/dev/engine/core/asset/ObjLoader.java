package dev.engine.core.asset;

import java.util.ArrayList;
import java.util.List;

public class ObjLoader implements AssetLoader<MeshData> {

    @Override
    public boolean supports(String path) {
        return path.toLowerCase().endsWith(".obj");
    }

    @Override
    public MeshData load(AssetSource.AssetData data) {
        var positions = new ArrayList<float[]>();
        var normals = new ArrayList<float[]>();
        var texCoords = new ArrayList<float[]>();

        var outPositions = new ArrayList<Float>();
        var outNormals = new ArrayList<Float>();
        var outTexCoords = new ArrayList<Float>();
        var outIndices = new ArrayList<Integer>();

        // Map from "v/vt/vn" string to vertex index for deduplication
        var vertexMap = new java.util.LinkedHashMap<String, Integer>();
        int vertexCount = 0;

        String text = new String(data.bytes());
        for (String line : text.lines().toList()) {
            line = line.trim();
            if (line.startsWith("v ")) {
                positions.add(parseFloats(line.substring(2)));
            } else if (line.startsWith("vn ")) {
                normals.add(parseFloats(line.substring(3)));
            } else if (line.startsWith("vt ")) {
                texCoords.add(parseFloats(line.substring(3)));
            } else if (line.startsWith("f ")) {
                String[] parts = line.substring(2).trim().split("\\s+");
                // Triangulate: fan from first vertex
                int[] faceIndices = new int[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    String key = parts[i];
                    Integer existing = vertexMap.get(key);
                    if (existing != null) {
                        faceIndices[i] = existing;
                    } else {
                        int idx = vertexCount++;
                        vertexMap.put(key, idx);
                        faceIndices[i] = idx;

                        // Parse v/vt/vn indices
                        String[] indices = key.split("/");
                        int vi = Integer.parseInt(indices[0]) - 1;
                        float[] pos = positions.get(vi);
                        outPositions.add(pos[0]);
                        outPositions.add(pos[1]);
                        outPositions.add(pos[2]);

                        if (indices.length > 1 && !indices[1].isEmpty()) {
                            int ti = Integer.parseInt(indices[1]) - 1;
                            float[] tc = texCoords.get(ti);
                            outTexCoords.add(tc[0]);
                            outTexCoords.add(tc.length > 1 ? tc[1] : 0f);
                        }

                        if (indices.length > 2 && !indices[2].isEmpty()) {
                            int ni = Integer.parseInt(indices[2]) - 1;
                            float[] n = normals.get(ni);
                            outNormals.add(n[0]);
                            outNormals.add(n[1]);
                            outNormals.add(n[2]);
                        }
                    }
                }

                // Fan triangulation
                for (int i = 1; i < parts.length - 1; i++) {
                    outIndices.add(faceIndices[0]);
                    outIndices.add(faceIndices[i]);
                    outIndices.add(faceIndices[i + 1]);
                }
            }
        }

        return new MeshData(
                toFloatArray(outPositions),
                toFloatArray(outNormals),
                toFloatArray(outTexCoords),
                outIndices.stream().mapToInt(Integer::intValue).toArray(),
                vertexCount,
                outIndices.size()
        );
    }

    @Override
    public Class<MeshData> assetType() {
        return MeshData.class;
    }

    private static float[] parseFloats(String s) {
        String[] parts = s.trim().split("\\s+");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i]);
        return result;
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
}
