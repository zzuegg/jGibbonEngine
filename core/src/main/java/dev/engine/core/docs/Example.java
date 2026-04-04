package dev.engine.core.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a website example showcase.
 * Uses the same interleaved block-comment/code pattern as {@code @Tutorial},
 * but produces compact card-style pages with screenshot previews.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Example {

    /** Title shown on the website. */
    String title();

    /** Short description for the example card. */
    String description() default "";

    /** Category for grouping (e.g., "Getting Started", "Rendering"). */
    String category() default "";

    /** Order within category (lower = first). */
    int order() default -1;

    /** Screenshot filename (from screenshot tests or manually placed). */
    String screenshot() default "";
}
