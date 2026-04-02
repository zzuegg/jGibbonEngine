package dev.engine.core.module.exception;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CyclicDependencyException extends ModuleException {

    private final List<String> cyclePath;

    public CyclicDependencyException(List<String> cyclePath) {
        super(buildMessage(cyclePath));
        this.cyclePath = Collections.unmodifiableList(cyclePath);
    }

    public List<String> getCyclePath() {
        return cyclePath;
    }

    private static String buildMessage(List<String> cyclePath) {
        return "Cyclic dependency detected: " + cyclePath.stream().collect(Collectors.joining(" \u2192 "));
    }
}
