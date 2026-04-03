package dev.engine.graphics.command;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.graphics.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

class DrawCallTest {
    HandlePool<PipelineResource> pipelinePool = new HandlePool<>();
    HandlePool<BufferResource> bufferPool = new HandlePool<>();
    HandlePool<VertexInputResource> vertexInputPool = new HandlePool<>();
    HandlePool<TextureResource> texturePool = new HandlePool<>();
    HandlePool<SamplerResource> samplerPool = new HandlePool<>();

    @AfterEach void resetValidation() { DrawCall.setValidation(true); }

    @Test void indexedDrawCallBuilds() {
        var pipeline = pipelinePool.allocate();
        var vbo = bufferPool.allocate();
        var ibo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();

        var call = DrawCall.indexed()
            .pipeline(pipeline)
            .vertices(vbo, vi)
            .indices(ibo)
            .count(36)
            .build();

        assertEquals(pipeline, call.pipeline());
        assertEquals(vbo, call.vertexBuffer());
        assertEquals(ibo, call.indexBuffer());
        assertEquals(36, call.indexCount());
    }

    @Test void nonIndexedDrawCall() {
        var pipeline = pipelinePool.allocate();
        var vbo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();

        var call = DrawCall.nonIndexed()
            .pipeline(pipeline)
            .vertices(vbo, vi)
            .count(3)
            .build();

        assertEquals(3, call.vertexCount());
        assertNull(call.indexBuffer());
    }

    @Test void validationRejectsMissingPipeline() {
        DrawCall.setValidation(true);
        var vbo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();

        assertThrows(IllegalStateException.class, () ->
            DrawCall.nonIndexed().vertices(vbo, vi).count(3).build());
    }

    @Test void noValidationAllowsMissingPipeline() {
        DrawCall.setValidation(false);
        var vbo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();

        assertDoesNotThrow(() ->
            DrawCall.nonIndexed().vertices(vbo, vi).count(3).build());
    }

    @Test void toCommandsProducesCorrectSequence() {
        var pipeline = pipelinePool.allocate();
        var vbo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();
        var ubo = bufferPool.allocate();

        var call = DrawCall.nonIndexed()
            .pipeline(pipeline)
            .vertices(vbo, vi)
            .uniform(0, ubo)
            .count(3)
            .build();

        var cmds = call.toCommands();
        assertInstanceOf(RenderCommand.BindPipeline.class, cmds.get(0));
        assertInstanceOf(RenderCommand.BindVertexBuffer.class, cmds.get(1));
        assertInstanceOf(RenderCommand.BindUniformBuffer.class, cmds.get(2));
        assertInstanceOf(RenderCommand.Draw.class, cmds.get(3));
    }

    @Test void drawCallViaRecorder() {
        var pipeline = pipelinePool.allocate();
        var vbo = bufferPool.allocate();
        var vi = vertexInputPool.allocate();

        var call = DrawCall.nonIndexed()
            .pipeline(pipeline)
            .vertices(vbo, vi)
            .count(3)
            .build();

        var recorder = new CommandRecorder();
        recorder.draw(call);
        var list = recorder.finish();
        assertEquals(3, list.size()); // BindPipeline, BindVertexBuffer, Draw
    }
}
