package dev.engine.core.docs;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a cross-cutting engine feature for the website.
 * Place on {@code package-info.java} files alongside {@link EngineModule},
 * or on dedicated feature marker classes.
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(EngineFeatures.class)
public @interface EngineFeature {

    /** Display name on the website. */
    String name();

    /** Short description for the feature card. */
    String description();

    /** Icon identifier for the landing page. */
    String icon() default "";
}
