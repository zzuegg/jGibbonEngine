package dev.engine.graphics.target;

import dev.engine.graphics.texture.TextureFormat;

import java.util.List;

public record RenderTargetDescriptor(int width, int height, List<TextureFormat> colorAttachments, TextureFormat depthFormat) {

    public static RenderTargetDescriptor color(int width, int height, TextureFormat format) {
        return new RenderTargetDescriptor(width, height, List.of(format), null);
    }

    public static RenderTargetDescriptor colorDepth(int width, int height, TextureFormat colorFormat, TextureFormat depthFormat) {
        return new RenderTargetDescriptor(width, height, List.of(colorFormat), depthFormat);
    }
}
