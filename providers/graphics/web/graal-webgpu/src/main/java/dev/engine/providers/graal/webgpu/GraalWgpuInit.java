package dev.engine.providers.graal.webgpu;

import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSNumber;

/**
 * Reads WebGPU adapter/device that were initialized by the host HTML page
 * before {@code GraalVM.run()} was called. The HTML must store them in
 * {@code globalThis._wgpu} with {@code globalThis._wgpuDevice} set to the device ID.
 */
public final class GraalWgpuInit {

    private GraalWgpuInit() {}

    /**
     * Returns the device handle ID stored by the host page, or 0 if not initialized.
     */
    public static int getDeviceId() { return getDeviceIdJS().asInt(); }
    public static int getAdapterId() { return getAdapterIdJS().asInt(); }

    @JS(value = "return globalThis._wgpuDevice || 0;")
    private static native JSNumber getDeviceIdJS();

    @JS(value = "return globalThis._wgpuAdapter || 0;")
    private static native JSNumber getAdapterIdJS();
}
