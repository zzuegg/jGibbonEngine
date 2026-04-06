package dev.engine.graphics.command;

import dev.engine.core.handle.Handle;
import dev.engine.core.handle.HandlePool;
import dev.engine.core.property.PropertyMap;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.PipelineResource;
import dev.engine.graphics.TextureResource;
import dev.engine.graphics.VertexInputResource;
import dev.engine.graphics.renderstate.BarrierScope;
import dev.engine.graphics.renderstate.BlendMode;
import dev.engine.graphics.renderstate.CullMode;
import dev.engine.graphics.renderstate.RenderState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandListTest {

    @Test void recordAndRetrieveCommands() {
        var ctx = new CommandRecorder();
        ctx.clear(0.1f, 0.1f, 0.1f, 1f);
        ctx.viewport(0, 0, 800, 600);
        ctx.setRenderState(PropertyMap.<RenderState>builder()
                .set(RenderState.DEPTH_TEST, true)
                .build());
        ctx.bindPipeline(new Handle<>(0, 0));
        ctx.bindVertexBuffer(new Handle<>(1, 0), new Handle<>(2, 0));
        ctx.draw(3, 0);

        var list = ctx.finish();
        assertEquals(6, list.commands().size());
        assertInstanceOf(RenderCommand.Clear.class, list.commands().get(0));
        assertInstanceOf(RenderCommand.Viewport.class, list.commands().get(1));
        assertInstanceOf(RenderCommand.SetRenderState.class, list.commands().get(2));
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

    @Test void setRenderStateWithMultipleProperties() {
        var ctx = new CommandRecorder();
        ctx.setRenderState(PropertyMap.<RenderState>builder()
                .set(RenderState.DEPTH_TEST, true)
                .set(RenderState.BLEND_MODE, BlendMode.ALPHA)
                .set(RenderState.CULL_MODE, CullMode.BACK)
                .set(RenderState.WIREFRAME, false)
                .build());
        ctx.scissor(10, 20, 100, 200);
        var list = ctx.finish();
        assertEquals(2, list.commands().size());
        assertInstanceOf(RenderCommand.SetRenderState.class, list.commands().get(0));
        assertInstanceOf(RenderCommand.Scissor.class, list.commands().get(1));

        var stateCmd = (RenderCommand.SetRenderState) list.commands().get(0);
        assertEquals(true, stateCmd.properties().get(RenderState.DEPTH_TEST));
        assertEquals(BlendMode.ALPHA, stateCmd.properties().get(RenderState.BLEND_MODE));
        assertEquals(CullMode.BACK, stateCmd.properties().get(RenderState.CULL_MODE));
    }

    @Test void setRenderStateCommand() {
        var state = PropertyMap.<RenderState>builder()
            .set(RenderState.DEPTH_TEST, true)
            .set(RenderState.BLEND_MODE, BlendMode.ALPHA)
            .build();
        var recorder = new CommandRecorder();
        recorder.setRenderState(state);
        var list = recorder.finish();
        assertInstanceOf(RenderCommand.SetRenderState.class, list.commands().getFirst());
    }

    @Test void dispatchCommand() {
        var recorder = new CommandRecorder();
        recorder.dispatch(8, 8, 1);
        var list = recorder.finish();
        var cmd = (RenderCommand.Dispatch) list.commands().getFirst();
        assertEquals(8, cmd.groupsX());
        assertEquals(1, cmd.groupsZ());
    }

    @Test void memoryBarrierCommand() {
        var recorder = new CommandRecorder();
        recorder.memoryBarrier(BarrierScope.STORAGE_BUFFER);
        var list = recorder.finish();
        assertInstanceOf(RenderCommand.MemoryBarrier.class, list.commands().getFirst());
    }

    @Test void pushConstantsCommand() {
        var recorder = new CommandRecorder();
        recorder.pushConstants(java.nio.ByteBuffer.allocate(64));
        var list = recorder.finish();
        assertInstanceOf(RenderCommand.PushConstants.class, list.commands().getFirst());
    }

    @Test void bindComputePipelineCommand() {
        var pool = new HandlePool<PipelineResource>();
        var recorder = new CommandRecorder();
        recorder.bindComputePipeline(pool.allocate());
        var list = recorder.finish();
        assertInstanceOf(RenderCommand.BindComputePipeline.class, list.commands().getFirst());
    }

    @Test void drawInstancedCommand() {
        var recorder = new CommandRecorder();
        recorder.drawInstanced(36, 0, 100, 0);
        var list = recorder.finish();
        var cmd = (RenderCommand.DrawInstanced) list.commands().getFirst();
        assertEquals(36, cmd.vertexCount());
        assertEquals(100, cmd.instanceCount());
    }

    @Test void drawIndexedInstancedCommand() {
        var recorder = new CommandRecorder();
        recorder.drawIndexedInstanced(36, 0, 50, 0);
        var list = recorder.finish();
        var cmd = (RenderCommand.DrawIndexedInstanced) list.commands().getFirst();
        assertEquals(36, cmd.indexCount());
        assertEquals(50, cmd.instanceCount());
    }

    @Test void copyBufferCommand() {
        var pool = new HandlePool<BufferResource>();
        var src = pool.allocate();
        var dst = pool.allocate();
        var recorder = new CommandRecorder();
        recorder.copyBuffer(src, dst, 0, 0, 1024);
        var list = recorder.finish();
        var cmd = (RenderCommand.CopyBuffer) list.commands().getFirst();
        assertEquals(1024, cmd.size());
    }

    @Test void copyTextureCommand() {
        var pool = new HandlePool<TextureResource>();
        var src = pool.allocate();
        var dst = pool.allocate();
        var recorder = new CommandRecorder();
        recorder.copyTexture(src, dst, 0, 0, 0, 0, 256, 256);
        var list = recorder.finish();
        assertInstanceOf(RenderCommand.CopyTexture.class, list.commands().getFirst());
    }

    @Test void blitTextureCommand() {
        var pool = new HandlePool<TextureResource>();
        var src = pool.allocate();
        var dst = pool.allocate();
        var recorder = new CommandRecorder();
        recorder.blitTexture(src, dst, 0, 0, 512, 512, 0, 0, 256, 256, true);
        var list = recorder.finish();
        var cmd = (RenderCommand.BlitTexture) list.commands().getFirst();
        assertTrue(cmd.linearFilter());
    }

    @Test void drawIndirectCommand() {
        var pool = new HandlePool<BufferResource>();
        var buf = pool.allocate();
        var recorder = new CommandRecorder();
        recorder.drawIndirect(buf, 0, 10, 16);
        var list = recorder.finish();
        var cmd = (RenderCommand.DrawIndirect) list.commands().getFirst();
        assertEquals(10, cmd.drawCount());
        assertEquals(16, cmd.stride());
    }

    @Test void drawIndexedIndirectCommand() {
        var pool = new HandlePool<BufferResource>();
        var buf = pool.allocate();
        var recorder = new CommandRecorder();
        recorder.drawIndexedIndirect(buf, 64, 5, 20);
        var list = recorder.finish();
        var cmd = (RenderCommand.DrawIndexedIndirect) list.commands().getFirst();
        assertEquals(5, cmd.drawCount());
        assertEquals(64, cmd.offset());
    }

    @Test void bindImageCommand() {
        var pool = new HandlePool<TextureResource>();
        var tex = pool.allocate();
        var recorder = new CommandRecorder();
        recorder.bindImage(0, tex, 0, true, true);
        var list = recorder.finish();
        var cmd = (RenderCommand.BindImage) list.commands().getFirst();
        assertEquals(0, cmd.unit());
        assertTrue(cmd.read());
        assertTrue(cmd.write());
    }

    @Test void bindImageConvenienceDefaults() {
        var pool = new HandlePool<TextureResource>();
        var tex = pool.allocate();
        var recorder = new CommandRecorder();
        recorder.bindImage(0, tex);
        var list = recorder.finish();
        var cmd = (RenderCommand.BindImage) list.commands().getFirst();
        assertEquals(0, cmd.mipLevel());
        assertTrue(cmd.read());
        assertTrue(cmd.write());
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
