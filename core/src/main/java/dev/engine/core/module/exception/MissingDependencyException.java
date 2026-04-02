package dev.engine.core.module.exception;

public class MissingDependencyException extends ModuleException {

    private final Class<?> requestingModule;
    private final Class<?> missingDependency;

    public MissingDependencyException(Class<?> requestingModule, Class<?> missingDependency) {
        super(buildMessage(requestingModule, missingDependency));
        this.requestingModule = requestingModule;
        this.missingDependency = missingDependency;
    }

    public Class<?> getRequestingModule() {
        return requestingModule;
    }

    public Class<?> getMissingDependency() {
        return missingDependency;
    }

    private static String buildMessage(Class<?> requestingModule, Class<?> missingDependency) {
        return "Module " + requestingModule.getSimpleName()
                + " requires " + missingDependency.getSimpleName()
                + ", which is not registered";
    }
}
