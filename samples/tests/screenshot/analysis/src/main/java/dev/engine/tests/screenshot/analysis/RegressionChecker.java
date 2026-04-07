package dev.engine.tests.screenshot.analysis;

import dev.engine.tests.screenshot.scenes.manifest.Manifest;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Checks the manifest for regressions and exits with appropriate code.
 * Used as a CI gate and local test gate.
 */
public final class RegressionChecker {

    private RegressionChecker() {}

    /**
     * Checks the manifest for failures.
     *
     * @return 0 if all comparisons pass (or no_reference/skipped), 1 if any fail
     */
    public static int check(Path manifestPath, Path referenceDir) throws Exception {
        var manifest = Manifest.readFrom(manifestPath);

        boolean hasFailures = manifest.comparisons.stream()
                .anyMatch(c -> "fail".equals(c.status));

        if (hasFailures) {
            System.err.println("REGRESSION DETECTED:");
            for (var comp : manifest.comparisons) {
                if ("fail".equals(comp.status)) {
                    String location;
                    if ("cross_backend".equals(comp.type)) {
                        location = comp.backendA + " vs " + comp.backendB;
                    } else {
                        location = comp.backend != null ? comp.backend : "unknown";
                    }
                    System.err.println("  FAIL: " + comp.scene + " [" + location + "] — " + comp.reason);
                }
            }
            return 1;
        }

        // Report known limitations as info
        long knownCount = manifest.comparisons.stream()
                .filter(c -> "known_limitation".equals(c.status)).count();
        if (knownCount > 0) {
            System.out.println("Known limitations (" + knownCount + "):");
            for (var comp : manifest.comparisons) {
                if ("known_limitation".equals(comp.status)) {
                    String location = "cross_backend".equals(comp.type)
                            ? comp.backendA + " vs " + comp.backendB
                            : comp.backend != null ? comp.backend : "unknown";
                    System.out.println("  KNOWN: " + comp.scene + " [" + location + "] — " + comp.reason);
                }
            }
        }

        boolean allNoReference = !manifest.comparisons.isEmpty()
                && manifest.comparisons.stream()
                    .allMatch(c -> "no_reference".equals(c.status) || "skipped".equals(c.status));

        if (allNoReference) {
            boolean refDirHasContent = Files.exists(referenceDir)
                    && Files.list(referenceDir).findAny().isPresent();
            if (!refDirHasContent) {
                System.out.println();
                System.out.println("WARNING: No local reference images found for profile '"
                        + manifest.profile + "'.");
                System.out.println("Screenshot tests ran but could not verify against references.");
                System.out.println();
                System.out.println("To generate local references, run:");
                System.out.println("  git stash");
                System.out.println("  git checkout main");
                System.out.println("  ./gradlew saveReferences -Pscreenshot.profile=local");
                System.out.println("  git checkout -");
                System.out.println("  git stash pop");
                System.out.println();
                System.out.println("This captures baseline screenshots from main so your changes");
                System.out.println("can be compared against them.");
            }
        }

        return 0;
    }

    /** Entry point for Gradle JavaExec task. Args: manifestPath referenceDir */
    public static void main(String[] args) throws Exception {
        var manifestPath = Path.of(args[0]);
        var referenceDir = Path.of(args[1]);
        int exitCode = check(manifestPath, referenceDir);
        System.exit(exitCode);
    }
}
