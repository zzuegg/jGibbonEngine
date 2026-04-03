package dev.engine.core.layout;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a record for compile-time struct layout generation.
 *
 * <p>The annotation processor generates a {@code <RecordName>_Layout} class
 * that registers PACKED and STD140 layouts in a {@code static {}} block,
 * eliminating the need for runtime reflection.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NativeStruct {}
