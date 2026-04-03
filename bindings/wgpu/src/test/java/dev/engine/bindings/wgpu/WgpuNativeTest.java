package dev.engine.bindings.wgpu;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for the wgpu-native FFM bindings.
 *
 * <p>These tests require the wgpu-native library to be available
 * (either installed or auto-downloaded). They are skipped via
 * {@code assumeTrue} if the library cannot be loaded.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WgpuNativeTest {

    @BeforeAll
    static void checkAvailable() {
        assumeTrue(WgpuNative.isAvailable(),
                "wgpu-native is not available — skipping WebGPU tests");
    }

    @Test
    @Order(1)
    void createAndReleaseInstance() {
        var instance = WgpuNative.createInstance(MemorySegment.NULL);
        assertNotNull(instance);
        assertNotEquals(MemorySegment.NULL, instance);

        WgpuNative.instanceRelease(instance);
    }

    @Test
    @Order(2)
    void requestAdapter() {
        var instance = WgpuNative.createInstance(MemorySegment.NULL);
        try {
            var adapter = WgpuNative.requestAdapterSync(instance, MemorySegment.NULL);
            assertNotNull(adapter);
            assertNotEquals(MemorySegment.NULL, adapter);

            WgpuNative.adapterRelease(adapter);
        } finally {
            WgpuNative.instanceRelease(instance);
        }
    }

    @Test
    @Order(3)
    void requestDevice() {
        var instance = WgpuNative.createInstance(MemorySegment.NULL);
        try {
            var adapter = WgpuNative.requestAdapterSync(instance, MemorySegment.NULL);
            try {
                var device = WgpuNative.requestDeviceSync(instance, adapter, MemorySegment.NULL);
                assertNotNull(device);
                assertNotEquals(MemorySegment.NULL, device);

                var queue = WgpuNative.deviceGetQueue(device);
                assertNotNull(queue);
                assertNotEquals(MemorySegment.NULL, queue);

                WgpuNative.deviceRelease(device);
            } finally {
                WgpuNative.adapterRelease(adapter);
            }
        } finally {
            WgpuNative.instanceRelease(instance);
        }
    }

    @Test
    @Order(4)
    void createBuffer() {
        var instance = WgpuNative.createInstance(MemorySegment.NULL);
        try {
            var adapter = WgpuNative.requestAdapterSync(instance, MemorySegment.NULL);
            try {
                var device = WgpuNative.requestDeviceSync(instance, adapter, MemorySegment.NULL);
                try (var arena = Arena.ofConfined()) {
                    // Create a 256-byte vertex buffer with COPY_DST so we can write to it
                    var buffer = WgpuNative.deviceCreateBuffer(device,
                            256,
                            WgpuNative.BUFFER_USAGE_VERTEX | WgpuNative.BUFFER_USAGE_COPY_DST,
                            false,
                            arena);
                    assertNotNull(buffer);
                    assertNotEquals(MemorySegment.NULL, buffer);

                    WgpuNative.bufferRelease(buffer);
                } finally {
                    WgpuNative.deviceRelease(device);
                }
            } finally {
                WgpuNative.adapterRelease(adapter);
            }
        } finally {
            WgpuNative.instanceRelease(instance);
        }
    }

    @Test
    @Order(5)
    void createBufferAndWriteViaQueue() {
        var instance = WgpuNative.createInstance(MemorySegment.NULL);
        try {
            var adapter = WgpuNative.requestAdapterSync(instance, MemorySegment.NULL);
            try {
                var device = WgpuNative.requestDeviceSync(instance, adapter, MemorySegment.NULL);
                var queue = WgpuNative.deviceGetQueue(device);
                try (var arena = Arena.ofConfined()) {
                    var buffer = WgpuNative.deviceCreateBuffer(device,
                            64,
                            WgpuNative.BUFFER_USAGE_UNIFORM | WgpuNative.BUFFER_USAGE_COPY_DST,
                            false,
                            arena);

                    // Write 16 floats (64 bytes) to the buffer
                    var data = arena.allocate(64);
                    for (int i = 0; i < 16; i++) {
                        data.setAtIndex(java.lang.foreign.ValueLayout.JAVA_FLOAT, i, (float) i);
                    }
                    WgpuNative.queueWriteBuffer(queue, buffer, 0, data, 64);

                    WgpuNative.bufferRelease(buffer);
                } finally {
                    WgpuNative.deviceRelease(device);
                }
            } finally {
                WgpuNative.adapterRelease(adapter);
            }
        } finally {
            WgpuNative.instanceRelease(instance);
        }
    }

    @Test
    @Order(6)
    void createShaderModule() {
        var instance = WgpuNative.createInstance(MemorySegment.NULL);
        try {
            var adapter = WgpuNative.requestAdapterSync(instance, MemorySegment.NULL);
            try {
                var device = WgpuNative.requestDeviceSync(instance, adapter, MemorySegment.NULL);
                try (var arena = Arena.ofConfined()) {
                    var wgsl = """
                            @vertex
                            fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4<f32> {
                                var pos = array<vec2<f32>, 3>(
                                    vec2<f32>( 0.0,  0.5),
                                    vec2<f32>(-0.5, -0.5),
                                    vec2<f32>( 0.5, -0.5)
                                );
                                return vec4<f32>(pos[idx], 0.0, 1.0);
                            }

                            @fragment
                            fn fs_main() -> @location(0) vec4<f32> {
                                return vec4<f32>(1.0, 0.0, 0.0, 1.0);
                            }
                            """;

                    var module = WgpuNative.deviceCreateShaderModuleWGSL(device, wgsl, arena);
                    assertNotNull(module);
                    assertNotEquals(MemorySegment.NULL, module);

                    WgpuNative.shaderModuleRelease(module);
                } finally {
                    WgpuNative.deviceRelease(device);
                }
            } finally {
                WgpuNative.adapterRelease(adapter);
            }
        } finally {
            WgpuNative.instanceRelease(instance);
        }
    }
}
