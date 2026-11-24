package com.flagship.starter.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative feature gate annotation for Spring beans (FR4).
 * <p>
 * Guards method execution based on feature flag evaluation.
 * If the flag evaluates to {@code false}, either the {@code fallbackMethod} is invoked,
 * or a {@code FeatureDisabledException} is thrown.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FeatureGuard {

    /**
     * The feature flag key to evaluate.
     */
    String value();

    /**
     * Optional name of a fallback method on the same target bean to invoke
     * when the flag evaluates to disabled. Must match the parameter types of the guarded method.
     */
    String fallbackMethod() default "";

    /**
     * Expected boolean state considered "enabled". Defaults to {@code true}.
     */
    boolean enabledValue() default true;
}
