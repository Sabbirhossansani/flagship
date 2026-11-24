package com.flagship.starter.aspect;

import com.flagship.core.model.EvaluationContext;
import com.flagship.starter.annotation.FeatureGuard;
import com.flagship.starter.client.FlagShipClient;
import com.flagship.starter.exception.FeatureDisabledException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * AOP Aspect intercepting methods annotated with {@link FeatureGuard} (FR4).
 */
@Aspect
public class FeatureGuardAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureGuardAspect.class);

    private final FlagShipClient flagShipClient;

    public FeatureGuardAspect(FlagShipClient flagShipClient) {
        this.flagShipClient = flagShipClient;
    }

    @Around("@annotation(guard)")
    public Object guardMethod(ProceedingJoinPoint joinPoint, FeatureGuard guard) throws Throwable {
        String flagKey = guard.value();
        EvaluationContext context = resolveContext(joinPoint);

        boolean isEnabled = flagShipClient.isEnabled(flagKey, context, false);
        boolean allowExecution = (isEnabled == guard.enabledValue());

        if (allowExecution) {
            return joinPoint.proceed();
        }

        // Feature is disabled
        String fallbackMethodName = guard.fallbackMethod();
        if (fallbackMethodName != null && !fallbackMethodName.isBlank()) {
            return invokeFallbackMethod(joinPoint, fallbackMethodName);
        }

        throw new FeatureDisabledException(flagKey);
    }

    private EvaluationContext resolveContext(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof EvaluationContext ctx) {
                    return ctx;
                }
            }
        }
        return EvaluationContext.builder().build();
    }

    private Object invokeFallbackMethod(ProceedingJoinPoint joinPoint, String fallbackMethodName) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Object target = joinPoint.getTarget();
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = joinPoint.getArgs();

        try {
            Method fallbackMethod = target.getClass().getMethod(fallbackMethodName, paramTypes);
            fallbackMethod.setAccessible(true);
            return fallbackMethod.invoke(target, args);
        } catch (NoSuchMethodException e) {
            log.error("Fallback method '{}' not found on class {}", fallbackMethodName, target.getClass().getName());
            throw new IllegalStateException("Fallback method '" + fallbackMethodName + "' not found on " + target.getClass().getName(), e);
        }
    }
}
