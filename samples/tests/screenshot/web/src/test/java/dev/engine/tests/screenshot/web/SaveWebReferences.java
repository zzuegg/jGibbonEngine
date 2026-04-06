package dev.engine.tests.screenshot.web;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies screenshots captured by the test run into the reference directory.
 *
 * <p>Run the tests first to capture screenshots, then run this to promote them:
 * <pre>{@code
 * ./gradlew :samples:tests:screenshot:web:test
 * ./gradlew :samples:tests:screenshot:web:saveReferences
 * }</pre>
 */
public class SaveWebReferences {

    public static void main(String[] args) throws IOException {
        var captureDir = Path.of("build/screenshots/webgpu-browser");
        var refDir = Path.of("src/test/resources/reference-screenshots/webgpu-browser");

        if (!Files.exists(captureDir)) {
            System.err.println("No captured screenshots found at " + captureDir);
            System.err.println("Run tests first: ./gradlew :samples:tests:screenshot:web:test");
            System.exit(1);
        }

        refDir.toFile().mkdirs();
        int saved = 0;

        try (var stream = Files.list(captureDir)) {
            var pngs = stream.filter(p -> p.toString().endsWith(".png")).sorted().toList();
            for (var png : pngs) {
                var dest = refDir.resolve(png.getFileName());
                Files.copy(png, dest, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  SAVED " + png.getFileName());
                saved++;
            }
        }

        System.out.println("\nSaved " + saved + " reference screenshots.");
        System.out.println("Commit them to track regressions:");
        System.out.println("  git add samples/tests/screenshot/web/src/test/resources/reference-screenshots/");
    }
}
