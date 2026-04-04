package dev.engine.core.tutorial;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a tutorial for the website.
 * The source file is processed by the tutorial generator to produce
 * documentation pages with interleaved prose and code.
 *
 * <p>Block comments ({@code /* ... * /}) become explanatory text.
 * Code between comments becomes syntax-highlighted code blocks.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tutorial {

    /** Title shown on the website. */
    String title();

    /** Category for grouping (e.g., "Getting Started", "Rendering"). */
    String category() default "";

    /** Order within category (lower = first). Defaults to filename prefix. */
    int order() default -1;

    /** Short description shown in the tutorial index. */
    String description() default "";
}
