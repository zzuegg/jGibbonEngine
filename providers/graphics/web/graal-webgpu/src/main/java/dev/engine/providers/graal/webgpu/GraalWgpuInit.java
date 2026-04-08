package dev.engine.providers.graal.webgpu;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Handles asynchronous WebGPU initialization via GraalJS.
 *
 * <p>Browser WebGPU requires Promises for adapter/device creation. This class
 * evaluates a top-level-await ES module that performs the async init and stores
 * the adapter/device in the global registry for {@link GraalWgpuBindings}.
 */
public final class GraalWgpuInit {

    private static final Logger log = LoggerFactory.getLogger(GraalWgpuInit.class);

    private GraalWgpuInit() {}

    /**
     * Initializes WebGPU adapter and device in the given GraalJS context.
     *
     * @param context the shared GraalJS context
     * @return the device handle ID, or 0 on failure
     */
    public static int initAsync(Context context) {
        try {
            Source initSource = Source.newBuilder("js", INIT_JS, "wgpu-init.mjs")
                    .mimeType("application/javascript+module")
                    .build();
            Value result = context.eval(initSource);
            int deviceId = result.asInt();
            if (deviceId > 0) {
                log.info("WebGPU device initialized, id={}", deviceId);
            } else {
                log.warn("WebGPU initialization returned 0 — no device");
            }
            return deviceId;
        } catch (IOException e) {
            log.error("Failed to initialize WebGPU", e);
            return 0;
        }
    }

    private static final String INIT_JS = """
            if (!globalThis._wgpu) {
                globalThis._wgpu = {};
                globalThis._wgpuNextId = 1;
            }
            var _reg = function(obj) {
                var id = globalThis._wgpuNextId++;
                globalThis._wgpu[id] = obj;
                return id;
            };

            var _deviceId = 0;
            try {
                var adapter = await navigator.gpu.requestAdapter({ powerPreference: 'high-performance' });
                if (adapter) {
                    var adapterId = _reg(adapter);
                    globalThis._wgpuAdapter = adapterId;

                    var device = await adapter.requestDevice();
                    if (device) {
                        _deviceId = _reg(device);
                        globalThis._wgpuDevice = _deviceId;
                        device.lost.then(function(info) {
                            console.error('[GraalWasm/WebGPU] Device lost: ' + info.message);
                        });
                    }
                }
            } catch(e) {
                console.error('[GraalWasm/WebGPU] Init failed: ' + e);
            }
            _deviceId;
            """;
}
