package dev.engine.providers.teavm.webgpu;

import dev.engine.core.asset.AssetSource;
import org.teavm.interop.Async;
import org.teavm.interop.AsyncCallback;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

import java.nio.charset.StandardCharsets;

/**
 * Asset source that loads files via the browser's {@code fetch()} API.
 *
 * <p>Uses TeaVM's {@code @Async} mechanism to bridge the Promise-based
 * fetch API into synchronous-looking Java calls, matching the pattern
 * used by {@link TeaVmWgpuInit}.
 *
 * <p>All assets (shaders, textures, models) are served from a base URL
 * relative to the web application root. The build task copies asset files
 * alongside the generated JS output so they are accessible via HTTP.
 */
public class FetchAssetSource implements AssetSource {

    private final String baseUrl;

    /**
     * @param baseUrl base URL prefix for asset paths (e.g. {@code "assets/"})
     */
    public FetchAssetSource(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override
    public AssetData load(String path) {
        String text = fetchTextSync(baseUrl + path);
        if (text == null) {
            return null;
        }
        return new AssetData(path, text.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean exists(String path) {
        return headSync(baseUrl + path);
    }

    // --- fetch() as text (for shaders and other text assets) ---

    @JSFunctor
    public interface StringCallback extends JSObject {
        void accept(String value);
    }

    @JSFunctor
    public interface BoolCallback extends JSObject {
        void accept(boolean value);
    }

    @Async
    private static native String fetchTextSync(String url);

    private static void fetchTextSync(String url, AsyncCallback<String> callback) {
        fetchTextJS(url, value -> callback.complete(value));
    }

    @JSBody(params = {"url", "callback"}, script = """
        fetch(url).then(function(response) {
            if (!response.ok) {
                console.warn('[FetchAssetSource] HTTP ' + response.status + ' for ' + url);
                callback(null);
                return;
            }
            return response.text();
        }).then(function(text) {
            if (text !== undefined) callback(text);
        }).catch(function(err) {
            console.error('[FetchAssetSource] Fetch failed:', url, err);
            callback(null);
        });
    """)
    private static native void fetchTextJS(String url, StringCallback callback);

    // --- HEAD request for exists() ---

    @Async
    private static native Boolean headSync(String url);

    private static void headSync(String url, AsyncCallback<Boolean> callback) {
        headJS(url, value -> callback.complete(value));
    }

    @JSBody(params = {"url", "callback"}, script = """
        fetch(url, {method: 'HEAD'}).then(function(response) {
            callback(response.ok);
        }).catch(function(err) {
            callback(false);
        });
    """)
    private static native void headJS(String url, BoolCallback callback);
}
