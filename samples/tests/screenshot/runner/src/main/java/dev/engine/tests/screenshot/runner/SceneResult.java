package dev.engine.tests.screenshot.runner;

import java.util.Map;

/**
 * Result of rendering a single scene on a single backend.
 */
public sealed interface SceneResult {

    /** Successful render — contains frame → screenshot path mappings. */
    record Success(Map<Integer, String> screenshotPaths) implements SceneResult {}

    /** Scene threw an exception (caught by the child process). */
    record ExceptionResult(String message, String stackTrace) implements SceneResult {}

    /** Child process crashed (segfault, native error — no result JSON written). */
    record Crash(int exitCode, String stderr, String stdout) implements SceneResult {}

    /** Child process exceeded the timeout. */
    record Timeout(String stderr, String stdout) implements SceneResult {}
}
