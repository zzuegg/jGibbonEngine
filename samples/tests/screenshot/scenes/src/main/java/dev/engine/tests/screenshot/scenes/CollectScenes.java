package dev.engine.tests.screenshot.scenes;

import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Pipeline Pass 1: Discovers all scenes and writes the initial manifest.
 *
 * <p>Args: {@code <outputPath> [profile]}
 */
public class CollectScenes {

    public static void main(String[] args) throws Exception {
        var outputPath = Path.of(args[0]);
        var profile = args.length > 1 ? args[1] : "local";

        var registry = new SceneRegistry();
        var manifest = new Manifest();

        manifest.timestamp = Instant.now().toString();
        manifest.profile = profile;
        manifest.javaVersion = System.getProperty("java.version");
        manifest.os = System.getProperty("os.name") + " " + System.getProperty("os.version");

        try {
            manifest.branch = execGit("rev-parse", "--abbrev-ref", "HEAD");
            manifest.commit = execGit("rev-parse", "--short", "HEAD");
        } catch (Exception e) {
            manifest.branch = "unknown";
            manifest.commit = "unknown";
        }

        for (var discovered : registry.scenes()) {
            var scene = new Manifest.Scene();
            scene.name = discovered.name();
            scene.category = discovered.category();
            scene.className = discovered.className();
            scene.fieldName = discovered.fieldName();
            var config = discovered.scene().config();
            scene.captureFrames = config.captureFrames();
            scene.tolerance = config.tolerance();
            scene.width = config.width();
            scene.height = config.height();
            scene.knownLimitations = config.knownLimitations().stream()
                    .map(kl -> new Manifest.KnownLimitation(kl.backend(), kl.reason()))
                    .toList();
            manifest.scenes.add(scene);
        }

        manifest.writeTo(outputPath);
        System.out.println("Collected " + manifest.scenes.size() + " scenes -> " + outputPath);
    }

    private static String execGit(String... args) throws Exception {
        var cmd = new ArrayList<String>();
        cmd.add("git");
        cmd.addAll(Arrays.asList(args));
        var p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        var result = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return result;
    }
}
