package dev.engine.core.mesh;

import dev.engine.core.asset.AssetLoader;
import dev.engine.core.asset.AssetSource;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        var outIndices = new ArrayList<Integer>();

        var vertexMap = new LinkedHashMap<String, Integer>();
        var vertexFloats = new ArrayList<float[]>();
        int vertexCount = 0;

        boolean hasNormals = false;
        boolean hasTexCoords = false;

        String text = new String(data.bytes());
        for (String line : text.lines().toList()) {
            line = line.trim();
            if (line.startsWith("v ")) {
                positions.add(parseFloats(line.substring(2)));
            } else if (line.startsWith("vn ")) {
                normals.add(parseFloats(line.substring(3)));
                hasNormals = true;
            } else if (line.startsWith("vt ")) {
                texCoords.add(parseFloats(line.substring(3)));
                hasTexCoords = true;
            } else if (line.startsWith("f ")) {
                String[] parts = line.substring(2).trim().split("\\s+");
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

                        String[] indices = key.split("/");
                        int vi = Integer.parseInt(indices[0]) - 1;
                        float[] pos = positions.get(vi);

                        var floats = new ArrayList<Float>();
                        floats.add(pos[0]); floats.add(pos[1]); floats.add(pos[2]);

                        if (hasTexCoords && indices.length > 1 && !indices[1].isEmpty()) {
                            int ti = Integer.parseInt(indices[1]) - 1;
                            float[] tc = texCoords.get(ti);
                            floats.add(tc[0]);
                            floats.add(tc.length > 1 ? tc[1] : 0f);
                        }

                        if (hasNormals && indices.length > 2 && !indices[2].isEmpty()) {
                            int ni = Integer.parseInt(indices[2]) - 1;
                            float[] n = normals.get(ni);
                            floats.add(n[0]); floats.add(n[1]); floats.add(n[2]);
                        }

                        float[] arr = new float[floats.size()];
                        for (int j = 0; j < floats.size(); j++) arr[j] = floats.get(j);
                        vertexFloats.add(arr);
                    }
                }

                for (int i = 1; i < parts.length - 1; i++) {
                    outIndices.add(faceIndices[0]);
                    outIndices.add(faceIndices[i]);
                    outIndices.add(faceIndices[i + 1]);
                }
            }
        }

        var attrs = new ArrayList<VertexAttribute>();
        int offset = 0;
        int location = 0;
        attrs.add(new VertexAttribute(location++, 3, ComponentType.FLOAT, false, offset));
        offset += 3 * Float.BYTES;
        if (hasTexCoords) {
            attrs.add(new VertexAttribute(location++, 2, ComponentType.FLOAT, false, offset));
            offset += 2 * Float.BYTES;
        }
        if (hasNormals) {
            attrs.add(new VertexAttribute(location++, 3, ComponentType.FLOAT, false, offset));
            offset += 3 * Float.BYTES;
        }
        var format = new VertexFormat(attrs, offset);

        ByteBuffer vertexData = ByteBuffer.allocateDirect(vertexCount * format.stride())
                .order(ByteOrder.nativeOrder());
        for (var floats : vertexFloats) {
            for (float f : floats) vertexData.putFloat(f);
        }
        vertexData.flip();

        return new MeshData(vertexData, format, outIndices.stream().mapToInt(Integer::intValue).toArray(),
                vertexCount, outIndices.size());
    }

    @Override
    public Class<MeshData> assetType() { return MeshData.class; }

    private static float[] parseFloats(String s) {
        String[] parts = s.trim().split("\\s+");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i]);
        return result;
    }
}
