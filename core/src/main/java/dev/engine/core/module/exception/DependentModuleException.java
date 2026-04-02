package dev.engine.core.module.exception;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class DependentModuleException extends ModuleException {

    private final Class<?> module;
    private final Set<Class<?>> dependents;

    public DependentModuleException(Class<?> module, Set<Class<?>> dependents) {
        super(buildMessage(module, dependents));
        this.module = module;
        this.dependents = Collections.unmodifiableSet(dependents);
    }

    public Class<?> getModule() {
        return module;
    }

    public Set<Class<?>> getDependents() {
        return dependents;
    }

    private static String buildMessage(Class<?> module, Set<Class<?>> dependents) {
        String dependentNames = dependents.stream()
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return "Cannot remove " + module.getSimpleName() + ": depended on by [" + dependentNames + "]";
    }
}
