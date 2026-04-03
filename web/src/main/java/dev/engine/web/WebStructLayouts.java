package dev.engine.web;

import dev.engine.core.layout.LayoutMode;
import dev.engine.core.layout.StructLayout;
import dev.engine.core.math.Mat4;
import dev.engine.core.math.Vec2;
import dev.engine.core.math.Vec3;
import dev.engine.core.memory.NativeMemory;
import dev.engine.core.shader.params.CameraParams;
import dev.engine.core.shader.params.EngineParams;
import dev.engine.core.shader.params.ObjectParams;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-registers StructLayouts for record types used by the Renderer.
 * Required for TeaVM which doesn't support Class.getRecordComponents().
 */
public final class WebStructLayouts {

    private WebStructLayouts() {}

    public static void registerAll() {
        registerMat4();
        registerEngineParams();
        registerCameraParams();
        registerObjectParams();
    }

    private static void registerMat4() {
        // Mat4: 16 floats = 64 bytes, written column-major for GPU
        var fields = List.of(
                new StructLayout.Field("m00", float.class, 0, 4, 4),
                new StructLayout.Field("m10", float.class, 4, 4, 4),
                new StructLayout.Field("m20", float.class, 8, 4, 4),
                new StructLayout.Field("m30", float.class, 12, 4, 4),
                new StructLayout.Field("m01", float.class, 16, 4, 4),
                new StructLayout.Field("m11", float.class, 20, 4, 4),
                new StructLayout.Field("m21", float.class, 24, 4, 4),
                new StructLayout.Field("m31", float.class, 28, 4, 4),
                new StructLayout.Field("m02", float.class, 32, 4, 4),
                new StructLayout.Field("m12", float.class, 36, 4, 4),
                new StructLayout.Field("m22", float.class, 40, 4, 4),
                new StructLayout.Field("m32", float.class, 44, 4, 4),
                new StructLayout.Field("m03", float.class, 48, 4, 4),
                new StructLayout.Field("m13", float.class, 52, 4, 4),
                new StructLayout.Field("m23", float.class, 56, 4, 4),
                new StructLayout.Field("m33", float.class, 60, 4, 4)
        );
        var layout = StructLayout.manual(Mat4.class, LayoutMode.PACKED, fields, 64,
                (mem, record) -> writeMat4(mem, 0, (Mat4) record));
        StructLayout.register(Mat4.class, LayoutMode.PACKED, layout);
        StructLayout.register(Mat4.class, LayoutMode.STD140, layout);
    }

    private static void registerEngineParams() {
        // EngineParams: time(float=4), deltaTime(float=4), resolution(Vec2=8), frameCount(int=4)
        // std140: 4+4+8+4 = 20, padded to 32 (16-byte boundary)
        var fields = List.of(
                new StructLayout.Field("time", float.class, 0, 4, 4),
                new StructLayout.Field("deltaTime", float.class, 4, 4, 4),
                new StructLayout.Field("resolution", Vec2.class, 8, 8, 8),
                new StructLayout.Field("frameCount", int.class, 16, 4, 4)
        );
        int totalSize = 32;
        var layout = StructLayout.manual(EngineParams.class, LayoutMode.STD140, fields, totalSize,
                (mem, record) -> {
                    var p = (EngineParams) record;
                    mem.putFloat(0, p.time());
                    mem.putFloat(4, p.deltaTime());
                    mem.putFloat(8, p.resolution().x());
                    mem.putFloat(12, p.resolution().y());
                    mem.putInt(16, p.frameCount());
                });
        StructLayout.register(EngineParams.class, LayoutMode.PACKED, layout);
        StructLayout.register(EngineParams.class, LayoutMode.STD140, layout);
    }

    private static void registerCameraParams() {
        // CameraParams: viewProjection(Mat4=64), view(Mat4=64), projection(Mat4=64),
        //               position(Vec3=12, padded to 16), near(float=4), far(float=4)
        // std140 total: 64+64+64+16+4+4 = 216 bytes
        var fields = new ArrayList<StructLayout.Field>();
        fields.add(new StructLayout.Field("viewProjection", Mat4.class, 0, 64, 16));
        fields.add(new StructLayout.Field("view", Mat4.class, 64, 64, 16));
        fields.add(new StructLayout.Field("projection", Mat4.class, 128, 64, 16));
        fields.add(new StructLayout.Field("position", Vec3.class, 192, 12, 16));
        fields.add(new StructLayout.Field("near", float.class, 208, 4, 4));
        fields.add(new StructLayout.Field("far", float.class, 212, 4, 4));
        int totalSize = 216;

        var layout = StructLayout.manual(CameraParams.class, LayoutMode.STD140, fields, totalSize,
                (mem, record) -> {
                    var p = (CameraParams) record;
                    writeMat4(mem, 0, p.viewProjection());
                    writeMat4(mem, 64, p.view());
                    writeMat4(mem, 128, p.projection());
                    writeVec3(mem, 192, p.position());
                    mem.putFloat(208, p.near());
                    mem.putFloat(212, p.far());
                });
        StructLayout.register(CameraParams.class, LayoutMode.PACKED, layout);
        StructLayout.register(CameraParams.class, LayoutMode.STD140, layout);
    }

    private static void registerObjectParams() {
        // ObjectParams: world(Mat4) = 64 bytes
        var fields = List.of(
                new StructLayout.Field("world", Mat4.class, 0, 64, 16)
        );
        var layout = StructLayout.manual(ObjectParams.class, LayoutMode.STD140, fields, 64,
                (mem, record) -> writeMat4(mem, 0, ((ObjectParams) record).world()));
        StructLayout.register(ObjectParams.class, LayoutMode.PACKED, layout);
        StructLayout.register(ObjectParams.class, LayoutMode.STD140, layout);
    }

    private static void writeMat4(NativeMemory mem, long offset, Mat4 m) {
        // Column-major for GPU
        mem.putFloat(offset,      m.m00()); mem.putFloat(offset + 4,  m.m10());
        mem.putFloat(offset + 8,  m.m20()); mem.putFloat(offset + 12, m.m30());
        mem.putFloat(offset + 16, m.m01()); mem.putFloat(offset + 20, m.m11());
        mem.putFloat(offset + 24, m.m21()); mem.putFloat(offset + 28, m.m31());
        mem.putFloat(offset + 32, m.m02()); mem.putFloat(offset + 36, m.m12());
        mem.putFloat(offset + 40, m.m22()); mem.putFloat(offset + 44, m.m32());
        mem.putFloat(offset + 48, m.m03()); mem.putFloat(offset + 52, m.m13());
        mem.putFloat(offset + 56, m.m23()); mem.putFloat(offset + 60, m.m33());
    }

    private static void writeVec3(NativeMemory mem, long offset, Vec3 v) {
        mem.putFloat(offset,     v.x());
        mem.putFloat(offset + 4, v.y());
        mem.putFloat(offset + 8, v.z());
    }
}
