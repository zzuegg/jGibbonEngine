package dev.engine.graphics.common;

import dev.engine.core.gpu.BufferWriter;
import dev.engine.core.handle.Handle;
import dev.engine.core.layout.LayoutMode;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.material.MaterialData;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.property.PropertyKey;
import dev.engine.graphics.shader.GlobalParamNames;
import dev.engine.graphics.shader.GlobalParamsRegistry;
import dev.engine.graphics.shader.params.ObjectParams;
import dev.engine.graphics.BufferResource;
import dev.engine.graphics.buffer.AccessPattern;
import dev.engine.graphics.buffer.BufferUsage;
import dev.engine.graphics.command.CommandRecorder;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages uniform buffer creation, layout computation, and per-frame/per-object upload.
 */
public class UniformManager {

    private final GpuResourceManager gpu;
    private final GlobalParamsRegistry globalParams;
    private final Map<String, Handle<BufferResource>> globalUbos = new HashMap<>();
    private final Map<String, StructLayout> globalLayouts = new HashMap<>();
    private final Map<String, Handle<BufferResource>> materialUbos = new HashMap<>();
    private final Map<String, Handle<BufferResource>> objectUbos = new HashMap<>();

    public UniformManager(GpuResourceManager gpu, GlobalParamsRegistry globalParams) {
        this.gpu = gpu;
        this.globalParams = globalParams;

        for (var entry : globalParams.entries()) {
            var layout = StructLayout.of(entry.recordType(), LayoutMode.STD140);
            globalLayouts.put(entry.name(), layout);
            globalUbos.put(entry.name(), gpu.createBuffer(layout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        }
    }

    public GlobalParamsRegistry globalParams() { return globalParams; }

    public <T extends Record> void registerGlobalParams(String name, Class<T> recordType, int binding) {
        globalParams.register(name, recordType, binding);
        var layout = StructLayout.of(recordType);
        globalLayouts.put(name, layout);
        globalUbos.put(name, gpu.createBuffer(layout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
    }

    public void updateGlobalParams(String name, Object data) {
        globalParams.update(name, data);
    }

    /** Uploads all per-frame global params to GPU (skips per-object). */
    public void uploadPerFrameGlobals() {
        for (var entry : globalParams.entries()) {
            if (entry.data() == null) continue;
            if (GlobalParamNames.OBJECT.equals(entry.name())) continue;
            var ubo = globalUbos.get(entry.name());
            var layout = globalLayouts.get(entry.name());
            if (ubo != null && layout != null) {
                try (var w = gpu.writeBuffer(ubo)) {
                    layout.write(w.memory(), 0, entry.data());
                }
            }
        }
    }

    /** Uploads per-object params and binds global UBOs to the draw command. */
    public Handle<BufferResource> uploadObjectParams(Handle<?> entity, dev.engine.core.math.Mat4 transform) {
        var objectLayout = globalLayouts.get(GlobalParamNames.OBJECT);
        if (objectLayout == null) return null;

        var objectKey = "obj_" + entity.index();
        var objectUbo = objectUbos.computeIfAbsent(objectKey, k ->
                gpu.createBuffer(
                        objectLayout.size(), BufferUsage.UNIFORM, AccessPattern.DYNAMIC));
        var objectParams = new ObjectParams(transform);
        try (var w = gpu.writeBuffer(objectUbo)) {
            objectLayout.write(w.memory(), 0, objectParams);
        }
        return objectUbo;
    }

    /** Binds all global param UBOs to the given command recorder. */
    public void bindGlobals(CommandRecorder draw, dev.engine.graphics.renderer.Renderable r, Handle<BufferResource> objectUbo) {
        for (var entry : globalParams.entries()) {
            Handle<BufferResource> ubo;
            if (GlobalParamNames.OBJECT.equals(entry.name())) {
                ubo = objectUbo;
            } else {
                ubo = globalUbos.get(entry.name());
            }
            if (ubo != null) {
                draw.bindUniformBuffer(
                        r.bindingFor(entry.name() + "Buffer", entry.binding()), ubo);
            }
        }
    }

    /** Uploads material data as a UBO and binds it. */
    public void uploadAndBindMaterial(MaterialData matData, Handle<?> entity, CommandRecorder draw, int bindingSlot) {
        var keys = matData.keys();
        if (keys.isEmpty()) return;

        var scalarKeys = keys.stream()
                .filter(k -> BufferWriter.supports(k.type()) && !RenderStateManager.isRenderStateKey(k))
                .sorted(Comparator.comparing(PropertyKey::name))
                .toList();

        if (scalarKeys.isEmpty()) return;

        int totalSize = 0;
        int maxAlign = 4;
        for (var key : scalarKeys) {
            if (key.type() == Vec3.class) {
                totalSize = align(totalSize, 16);
                totalSize += 16;
                maxAlign = Math.max(maxAlign, 16);
            } else if (key.type() == dev.engine.core.math.Vec4.class || key.type() == dev.engine.core.math.Mat4.class) {
                totalSize = align(totalSize, 16);
                totalSize += BufferWriter.sizeOf(key.type());
                maxAlign = Math.max(maxAlign, 16);
            } else if (key.type() == Vec2.class) {
                totalSize = align(totalSize, 8);
                totalSize += BufferWriter.sizeOf(key.type());
                maxAlign = Math.max(maxAlign, 8);
            } else {
                totalSize += BufferWriter.sizeOf(key.type());
            }
        }
        totalSize = align(totalSize, maxAlign);

        final int uboSize = Math.max(totalSize, 16);
        var uboKey = "mat_" + entity.index();
        var ubo = materialUbos.computeIfAbsent(uboKey, k ->
                gpu.createBuffer(
                        uboSize, BufferUsage.UNIFORM, AccessPattern.DYNAMIC));

        try (var w = gpu.writeBuffer(ubo)) {
            int offset = 0;
            for (var key : scalarKeys) {
                var value = matData.get(key);
                if (value == null) continue;

                if (key.type() == Vec3.class) {
                    offset = align(offset, 16);
                    BufferWriter.write(w.memory(), offset, value);
                    offset += 16;
                } else {
                    BufferWriter.write(w.memory(), offset, value);
                    offset += BufferWriter.sizeOf(key.type());
                }
            }
        }

        draw.bindUniformBuffer(bindingSlot, ubo);
    }

    public void close() {
        for (var ubo : globalUbos.values()) gpu.destroyBuffer(ubo);
        globalUbos.clear();
        for (var ubo : materialUbos.values()) gpu.destroyBuffer(ubo);
        materialUbos.clear();
        for (var ubo : objectUbos.values()) gpu.destroyBuffer(ubo);
        objectUbos.clear();
    }

    private static int align(int offset, int alignment) {
        return (offset + alignment - 1) & ~(alignment - 1);
    }
}
