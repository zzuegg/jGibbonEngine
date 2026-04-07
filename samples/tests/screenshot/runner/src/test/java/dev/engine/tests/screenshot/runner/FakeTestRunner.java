package dev.engine.tests.screenshot.runner;

import java.nio.file.Path;
import java.util.List;

/**
 * Test double for AbstractTestRunner that returns preconfigured results.
 */
class FakeTestRunner extends AbstractTestRunner {

    @FunctionalInterface
    interface ResultFactory {
        SceneResult create(String className, String fieldName, String backend, Path outputDir);
    }

    private final List<String> backends;
    private final ResultFactory resultFactory;

    FakeTestRunner(List<String> backends, ResultFactory resultFactory) {
        this.backends = backends;
        this.resultFactory = resultFactory;
    }

    @Override
    public List<String> backends() { return backends; }

    @Override
    protected SceneResult runScene(String className, String fieldName, String backend,
                                    Path outputDir) {
        return resultFactory.create(className, fieldName, backend, outputDir);
    }
}
