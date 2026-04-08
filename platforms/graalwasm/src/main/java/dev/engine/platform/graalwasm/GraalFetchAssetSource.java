package dev.engine.platform.graalwasm;

import dev.engine.core.asset.AssetSource;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Asset source that loads files via the browser's {@code fetch()} API
 * through GraalJS polyglot.
 *
 * <p>Uses top-level await in ES module evaluation to bridge the async
 * {@code fetch()} into synchronous Java calls.
 */
class GraalFetchAssetSource implements AssetSource {

    private final Context context;
    private final String baseUrl;

    GraalFetchAssetSource(Context context, String baseUrl) {
        this.context = context;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override
    public AssetData load(String path) {
        String url = baseUrl + path;
        try {
            Source fetchSrc = Source.newBuilder("js",
                    "var _r = await fetch('" + escapeJs(url) + "');\n" +
                    "_r.ok ? await _r.text() : null;",
                    "fetch-" + path + ".mjs")
                    .mimeType("application/javascript+module")
                    .build();
            Value result = context.eval(fetchSrc);
            if (result.isNull()) return null;
            String text = result.asString();
            return new AssetData(path, text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean exists(String path) {
        String url = baseUrl + path;
        try {
            Source headSrc = Source.newBuilder("js",
                    "var _r = await fetch('" + escapeJs(url) + "', {method:'HEAD'});\n" +
                    "_r.ok;",
                    "head-" + path + ".mjs")
                    .mimeType("application/javascript+module")
                    .build();
            return context.eval(headSrc).asBoolean();
        } catch (IOException e) {
            return false;
        }
    }

    private static String escapeJs(String s) {
        return s.replace("'", "\\'");
    }
}
