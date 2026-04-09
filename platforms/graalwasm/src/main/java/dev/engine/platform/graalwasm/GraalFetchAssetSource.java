package dev.engine.platform.graalwasm;

import dev.engine.core.asset.AssetSource;
import org.graalvm.webimage.api.JS;
import org.graalvm.webimage.api.JSBoolean;
import org.graalvm.webimage.api.JSString;

import java.nio.charset.StandardCharsets;

/**
 * Loads assets via browser {@code XMLHttpRequest} (synchronous) using {@code @JS} interop.
 */
public class GraalFetchAssetSource implements AssetSource {

    private final String baseUrl;

    public GraalFetchAssetSource(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override
    public AssetData load(String path) {
        String url = baseUrl + path;
        JSString jsResult = fetchSync(JSString.of(url));
        if (jsResult == null) return null;
        byte[] bytes = jsResult.asString().getBytes(StandardCharsets.ISO_8859_1);
        return new AssetData(path, bytes);
    }

    @Override
    public boolean exists(String path) {
        return headRequest(JSString.of(baseUrl + path)).asBoolean();
    }

    @JS(args = "url", value = """
        try {
            var xhr = new XMLHttpRequest();
            xhr.open('GET', url, false);
            xhr.overrideMimeType('text/plain; charset=x-user-defined');
            xhr.send();
            if (xhr.status === 200) return xhr.responseText;
            return null;
        } catch(e) { return null; }
    """)
    private static native JSString fetchSync(JSString url);

    @JS(args = "url", value = """
        try {
            var xhr = new XMLHttpRequest();
            xhr.open('HEAD', url, false);
            xhr.send();
            return xhr.status === 200;
        } catch(e) { return false; }
    """)
    private static native JSBoolean headRequest(JSString url);
}
