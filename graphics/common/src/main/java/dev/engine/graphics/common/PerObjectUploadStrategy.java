package dev.engine.graphics.common;
import dev.engine.graphics.renderer.*;

import dev.engine.core.handle.Handle;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.RenderDevice;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferDescriptor;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;
import dev.engine.graphics.common.material.MaterialCompiler;

import java.util.List;

/**
 * Default upload strategy: one UBO write + bind per draw call.
 * Simple and compatible with all backends. Not optimal for large scenes.
 */
public class PerObjectUploadStrategy implements UploadStrategy {

    private final StructLayout mat4Layout = StructLayout.of(Mat4.class);
    private Handle<BufferResource> mvpUbo;
    private Handle<BufferResource> materialUbo;

    @Override
    public void prepare(RenderDevice device, List<DrawCommand> batch) {
        if (mvpUbo == null) {
            mvpUbo = device.createBuffer(new BufferDescriptor(mat4Layout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        }
    }

    @Override
    public void record(RenderDevice device, CommandRecorder recorder, DrawCommand command, int index) {
        // Upload MVP
        try (var w = device.writeBuffer(mvpUbo)) {
            mat4Layout.write(w.memory(), 0, command.transform());
        }
        recorder.bindUniformBuffer(0, mvpUbo);

        // Upload material data if present
        if (command.materialData() != null && command.materialData().size() > 0) {
            // For now, we still need the Material reference for serialization
            // In the future, MaterialCompiler should work from PropertyMap directly
        }
    }

    @Override
    public void finish(RenderDevice device) {
        // No-op for per-object strategy
    }
}
