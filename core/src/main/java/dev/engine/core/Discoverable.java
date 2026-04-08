package dev.engine.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class for compile-time discovery. The annotation processor generates
 * a {@code DiscoverableRegistry} class in the same package that provides hard
 * references to all discoverable classes, ensuring they survive TeaVM's dead
 * code elimination.
 *
 * <p>Classes annotated with {@code @NativeStruct} are implicitly discoverable
 * (the processor handles both annotations).
 *
 * <p>Usage: annotate classes whose existence needs to be known at runtime
 * without relying on classpath scanning or {@code Class.forName()}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Discoverable {}
