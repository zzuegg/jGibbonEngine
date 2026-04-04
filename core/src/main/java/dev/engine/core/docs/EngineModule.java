package dev.engine.core.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a package as an engine module for the website.
 * Place on {@code package-info.java} files. The site generator scans
 * these annotations to produce module overview pages and landing page data.
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EngineModule {

    /** Display name on the website. */
    String name();

    /** Short description for cards and overview pages. */
    String description();

    /** Grouping category (e.g., "Core", "Graphics Backend", "Provider"). */
    String category();

    /** Feature tags shown on the module page. */
    String[] features() default {};

    /** Icon identifier for the landing page (maps to emoji or icon class). */
    String icon() default "";
}
