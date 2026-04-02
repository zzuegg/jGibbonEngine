package dev.engine.core.mesh;

import java.util.List;

public record VertexFormat(List<VertexAttribute> attributes, int stride) {

    public static VertexFormat of(VertexAttribute... attrs) {
        int stride = 0;
        for (var attr : attrs) {
            stride += attr.sizeInBytes();
        }
        return new VertexFormat(List.of(attrs), stride);
    }
}
