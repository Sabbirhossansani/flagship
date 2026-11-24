package com.flagship.starter.aspect;

import com.flagship.core.model.EvaluationContext;
import com.flagship.starter.annotation.FeatureGuard;
import com.flagship.starter.client.FlagShipClient;
import com.flagship.starter.exception.FeatureDisabledException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeatureGuardAspectUnitTest {

    static class SampleService {
        @FeatureGuard("new-feature")
        public String executeGuarded() {
            return "real-execution";
        }

        @FeatureGuard(value = "new-feature", fallbackMethod = "executeFallback")
        public String executeWithFallback() {
            return "real-execution";
        }

        public String executeFallback() {
            return "fallback-execution";
        }
    }

    @Test
    @DisplayName("@FeatureGuard: Executes method when flag evaluates to true")
    void flagEnabled_executesMethod() {
        FlagShipClient client = mock(FlagShipClient.class);
        when(client.isEnabled(eq("new-feature"), any(EvaluationContext.class), eq(false))).thenReturn(true);

        FeatureGuardAspect aspect = new FeatureGuardAspect(client);
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        SampleService proxy = factory.getProxy();

        assertThat(proxy.executeGuarded()).isEqualTo("real-execution");
    }

    @Test
    @DisplayName("@FeatureGuard: Invokes fallbackMethod when flag is disabled")
    void flagDisabled_invokesFallbackMethod() {
        FlagShipClient client = mock(FlagShipClient.class);
        when(client.isEnabled(eq("new-feature"), any(EvaluationContext.class), eq(false))).thenReturn(false);

        FeatureGuardAspect aspect = new FeatureGuardAspect(client);
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        SampleService proxy = factory.getProxy();

        assertThat(proxy.executeWithFallback()).isEqualTo("fallback-execution");
    }

    @Test
    @DisplayName("@FeatureGuard: Throws FeatureDisabledException when flag disabled and no fallback defined")
    void flagDisabled_noFallback_throwsException() {
        FlagShipClient client = mock(FlagShipClient.class);
        when(client.isEnabled(eq("new-feature"), any(EvaluationContext.class), eq(false))).thenReturn(false);

        FeatureGuardAspect aspect = new FeatureGuardAspect(client);
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(aspect);
        SampleService proxy = factory.getProxy();

        assertThatThrownBy(proxy::executeGuarded)
                .isInstanceOf(FeatureDisabledException.class)
                .hasMessageContaining("new-feature");
    }
}
