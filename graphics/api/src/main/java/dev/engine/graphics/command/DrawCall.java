package dev.engine.graphics.command;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.*;

import java.nio.ByteBuffer;
import java.util.*;

public final class DrawCall {
    private static volatile boolean validation = true;

    private final Handle<PipelineResource> pipeline;
    private final Handle<BufferResource> vertexBuffer;
    private final Handle<VertexInputResource> vertexInput;
    private final Handle<BufferResource> indexBuffer;
    private final int vertexCount;
    private final int indexCount;
    private final int firstVertex;
    private final int firstIndex;
    private final Map<Integer, Handle<BufferResource>> uniformBuffers;
    private final Map<Integer, TextureBinding> textureBindings;
    private final Map<Integer, Handle<BufferResource>> storageBuffers;
    private final ByteBuffer pushConstants;

    public record TextureBinding(Handle<TextureResource> texture, Handle<SamplerResource> sampler) {}

    private DrawCall(Handle<PipelineResource> pipeline,
                     Handle<BufferResource> vertexBuffer,
                     Handle<VertexInputResource> vertexInput,
                     Handle<BufferResource> indexBuffer,
                     int vertexCount, int indexCount,
                     int firstVertex, int firstIndex,
                     Map<Integer, Handle<BufferResource>> uniformBuffers,
                     Map<Integer, TextureBinding> textureBindings,
                     Map<Integer, Handle<BufferResource>> storageBuffers,
                     ByteBuffer pushConstants) {
        this.pipeline = pipeline;
        this.vertexBuffer = vertexBuffer;
        this.vertexInput = vertexInput;
        this.indexBuffer = indexBuffer;
        this.vertexCount = vertexCount;
        this.indexCount = indexCount;
        this.firstVertex = firstVertex;
        this.firstIndex = firstIndex;
        this.uniformBuffers = Map.copyOf(uniformBuffers);
        this.textureBindings = Map.copyOf(textureBindings);
        this.storageBuffers = Map.copyOf(storageBuffers);
        this.pushConstants = pushConstants;
    }

    public Handle<PipelineResource> pipeline() { return pipeline; }
    public Handle<BufferResource> vertexBuffer() { return vertexBuffer; }
    public Handle<VertexInputResource> vertexInput() { return vertexInput; }
    public Handle<BufferResource> indexBuffer() { return indexBuffer; }
    public int vertexCount() { return vertexCount; }
    public int indexCount() { return indexCount; }

    public static void setValidation(boolean enabled) { validation = enabled; }

    public static IndexedBuilder indexed() { return new IndexedBuilder(); }
    public static NonIndexedBuilder nonIndexed() { return new NonIndexedBuilder(); }

    /** Converts to a list of RenderCommands for submission. */
    public List<RenderCommand> toCommands() {
        var cmds = new ArrayList<RenderCommand>();
        if (pipeline != null) cmds.add(new RenderCommand.BindPipeline(pipeline));
        if (vertexBuffer != null && vertexInput != null) cmds.add(new RenderCommand.BindVertexBuffer(vertexBuffer, vertexInput));
        if (indexBuffer != null) cmds.add(new RenderCommand.BindIndexBuffer(indexBuffer));
        uniformBuffers.forEach((binding, buf) -> cmds.add(new RenderCommand.BindUniformBuffer(binding, buf)));
        textureBindings.forEach((unit, tb) -> {
            cmds.add(new RenderCommand.BindTexture(unit, tb.texture()));
            if (tb.sampler() != null) cmds.add(new RenderCommand.BindSampler(unit, tb.sampler()));
        });
        storageBuffers.forEach((binding, buf) -> cmds.add(new RenderCommand.BindStorageBuffer(binding, buf)));
        if (pushConstants != null) cmds.add(new RenderCommand.PushConstants(pushConstants));
        if (indexBuffer != null) {
            cmds.add(new RenderCommand.DrawIndexed(indexCount, firstIndex));
        } else {
            cmds.add(new RenderCommand.Draw(vertexCount, firstVertex));
        }
        return cmds;
    }

    // --- Builders ---

    private static abstract class BaseBuilder<B extends BaseBuilder<B>> {
        Handle<PipelineResource> pipeline;
        Handle<BufferResource> vertexBuffer;
        Handle<VertexInputResource> vertexInput;
        Map<Integer, Handle<BufferResource>> uniformBuffers = new LinkedHashMap<>();
        Map<Integer, TextureBinding> textureBindings = new LinkedHashMap<>();
        Map<Integer, Handle<BufferResource>> storageBuffers = new LinkedHashMap<>();
        ByteBuffer pushConstants;

        @SuppressWarnings("unchecked")
        B self() { return (B) this; }

        public B pipeline(Handle<PipelineResource> p) { this.pipeline = p; return self(); }
        public B vertices(Handle<BufferResource> vbo, Handle<VertexInputResource> vi) {
            this.vertexBuffer = vbo; this.vertexInput = vi; return self();
        }
        public B uniform(int binding, Handle<BufferResource> buf) { uniformBuffers.put(binding, buf); return self(); }
        public B texture(int unit, Handle<TextureResource> tex, Handle<SamplerResource> sampler) {
            textureBindings.put(unit, new TextureBinding(tex, sampler)); return self();
        }
        public B storage(int binding, Handle<BufferResource> buf) { storageBuffers.put(binding, buf); return self(); }
        public B pushConstants(ByteBuffer data) { this.pushConstants = data; return self(); }

        void validate() {
            if (!validation) return;
            if (pipeline == null) throw new IllegalStateException("DrawCall requires a pipeline");
            if (vertexBuffer == null) throw new IllegalStateException("DrawCall requires a vertex buffer");
        }
    }

    public static final class IndexedBuilder extends BaseBuilder<IndexedBuilder> {
        private Handle<BufferResource> indexBuffer;
        private int indexCount;
        private int firstIndex;

        public IndexedBuilder indices(Handle<BufferResource> ibo) { this.indexBuffer = ibo; return this; }
        public IndexedBuilder count(int count) { this.indexCount = count; return this; }
        public IndexedBuilder firstIndex(int first) { this.firstIndex = first; return this; }

        public DrawCall build() {
            validate();
            if (validation && indexBuffer == null) throw new IllegalStateException("Indexed DrawCall requires an index buffer");
            return new DrawCall(pipeline, vertexBuffer, vertexInput, indexBuffer,
                    0, indexCount, 0, firstIndex, uniformBuffers, textureBindings, storageBuffers, pushConstants);
        }
    }

    public static final class NonIndexedBuilder extends BaseBuilder<NonIndexedBuilder> {
        private int vertexCount;
        private int firstVertex;

        public NonIndexedBuilder count(int count) { this.vertexCount = count; return this; }
        public NonIndexedBuilder firstVertex(int first) { this.firstVertex = first; return this; }

        public DrawCall build() {
            validate();
            return new DrawCall(pipeline, vertexBuffer, vertexInput, null,
                    vertexCount, 0, firstVertex, 0, uniformBuffers, textureBindings, storageBuffers, pushConstants);
        }
    }
}
