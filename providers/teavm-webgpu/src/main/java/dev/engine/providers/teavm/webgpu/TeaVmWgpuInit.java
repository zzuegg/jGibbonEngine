package dev.engine.providers.teavm.webgpu;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;

/**
 * Handles asynchronous WebGPU initialization in the browser.
 *
 * <p>Browser WebGPU requires Promises for adapter/device creation.
 * This class uses TeaVM's {@code @Async} mechanism to bridge Promise-based
 * JS APIs into synchronous-looking Java calls.
 *
 * <p>After calling {@link #initAsync()}, the adapter and device are stored
 * in the global JS registry so that {@link TeaVmWgpuBindings} can access them.
 */
public final class TeaVmWgpuInit {

    private TeaVmWgpuInit() {}

    @JSFunctor
    public interface IntCallback extends JSObject {
        void accept(int value);
    }

    /**
     * Requests a WebGPU adapter and device asynchronously.
     * Blocks (via TeaVM async transform) until both are available.
     *
     * @return the device handle ID in the global registry, or 0 on failure
     */
    @Async
    public static native int initAsync();

    private static void initAsync(AsyncCallback<Integer> callback) {
        initAsyncJS(value -> callback.complete(value));
    }

    @JSBody(params = "callback", script = """
        if (!window._wgpu) {
            window._wgpu = {};
            window._wgpuNextId = 1;
        }
        navigator.gpu.requestAdapter().then(function(adapter) {
            if (!adapter) {
                console.error('[TeaVM/WebGPU] No adapter found');
                callback.accept(0);
                return;
            }
            var adapterId = window._wgpuNextId++;
            window._wgpu[adapterId] = adapter;
            window._wgpuAdapter = adapterId;
            return adapter.requestDevice();
        }).then(function(device) {
            if (!device) {
                console.error('[TeaVM/WebGPU] No device found');
                callback.accept(0);
                return;
            }
            var deviceId = window._wgpuNextId++;
            window._wgpu[deviceId] = device;
            window._wgpuDevice = deviceId;
            device.lost.then(function(info) {
                console.error('[TeaVM/WebGPU] Device lost: ' + info.message);
            });
            callback.accept(deviceId);
        }).catch(function(err) {
            console.error('[TeaVM/WebGPU] Init failed: ' + err);
            callback.accept(0);
        });
    """)
    private static native void initAsyncJS(IntCallback callback);
}
