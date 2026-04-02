package dev.engine.graphics.command;

import dev.engine.core.handle.Handle;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.VertexInputResource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandListTest {

    @Test void recordAndRetrieveCommands() {
        var ctx = new CommandRecorder();
        ctx.clear(0.1f, 0.1f, 0.1f, 1f);
        ctx.viewport(0, 0, 800, 600);
        ctx.setDepthTest(true);
        ctx.bindPipeline(new Handle<>(0, 0));
        ctx.bindVertexBuffer(new Handle<>(1, 0), new Handle<>(2, 0));
        ctx.draw(3, 0);

        var list = ctx.finish();
        assertEquals(6, list.commands().size());
        assertInstanceOf(RenderCommand.Clear.class, list.commands().get(0));
        assertInstanceOf(RenderCommand.Viewport.class, list.commands().get(1));
        assertInstanceOf(RenderCommand.SetDepthTest.class, list.commands().get(2));
        assertInstanceOf(RenderCommand.BindPipeline.class, list.commands().get(3));
        assertInstanceOf(RenderCommand.BindVertexBuffer.class, list.commands().get(4));
        assertInstanceOf(RenderCommand.Draw.class, list.commands().get(5));
    }

    @Test void commandListIsImmutable() {
        var ctx = new CommandRecorder();
        ctx.draw(3, 0);
        var list = ctx.finish();
        assertThrows(UnsupportedOperationException.class, () ->
                list.commands().add(new RenderCommand.Draw(1, 0)));
    }

    @Test void clearHasColor() {
        var ctx = new CommandRecorder();
        ctx.clear(1f, 0f, 0f, 1f);
        var cmd = (RenderCommand.Clear) ctx.finish().commands().getFirst();
        assertEquals(1f, cmd.r());
        assertEquals(0f, cmd.g());
    }

    @Test void drawIndexedRecordsCorrectly() {
        var ctx = new CommandRecorder();
        ctx.bindIndexBuffer(new Handle<>(5, 0));
        ctx.drawIndexed(36, 0);
        var list = ctx.finish();
        assertEquals(2, list.commands().size());
        var indexed = (RenderCommand.DrawIndexed) list.commands().get(1);
        assertEquals(36, indexed.indexCount());
    }

    @Test void allStateCommandsRecorded() {
        var ctx = new CommandRecorder();
        ctx.setDepthTest(true);
        ctx.setBlending(true);
        ctx.setCullFace(true);
        ctx.setWireframe(false);
        ctx.scissor(10, 20, 100, 200);
        var list = ctx.finish();
        assertEquals(5, list.commands().size());
    }

    @Test void uniformAndTextureBindingRecorded() {
        var ctx = new CommandRecorder();
        ctx.bindUniformBuffer(0, new Handle<>(1, 0));
        ctx.bindTexture(0, new Handle<>(2, 0));
        ctx.bindSampler(0, new Handle<>(3, 0));
        var list = ctx.finish();
        assertEquals(3, list.commands().size());
        assertInstanceOf(RenderCommand.BindUniformBuffer.class, list.commands().get(0));
        assertInstanceOf(RenderCommand.BindTexture.class, list.commands().get(1));
        assertInstanceOf(RenderCommand.BindSampler.class, list.commands().get(2));
    }
}
