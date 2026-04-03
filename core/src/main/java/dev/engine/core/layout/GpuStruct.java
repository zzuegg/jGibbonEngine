package dev.engine.core.layout;

import java.lang.annotation.*;

/**
 * Marks a record as a GPU struct, triggering compile-time generation of a
 * {@code *_NativeStruct} class that provides reflection-free {@link StructLayout}
 * registration for all platforms (desktop, web/TeaVM, mobile).
 *
 * <p>The annotation processor computes field offsets, sizes, and alignment
 * for each requested {@link LayoutMode} and generates direct-access write
 * methods — no {@link java.lang.invoke.MethodHandle} or reflection at runtime.
 *
 * <p>Usage:
 * <pre>{@code
 * @GpuStruct
 * public record CameraParams(Mat4 viewProjection, Vec3 position, float near, float far) {}
 * }</pre>
 *
 * <p>At startup, call the generated {@code GeneratedLayouts.init()} or let
 * {@link StructLayout#of} auto-discover the generated class via {@code Class.forName()}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface GpuStruct {
}
