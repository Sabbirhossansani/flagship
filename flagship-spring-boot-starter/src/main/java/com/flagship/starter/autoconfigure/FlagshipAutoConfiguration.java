package com.flagship.starter.autoconfigure;

import com.flagship.core.evaluation.FlagEvaluationEngine;
import com.flagship.starter.actuator.FlagshipEndpoint;
import com.flagship.starter.aspect.FeatureGuardAspect;
import com.flagship.starter.client.DefaultFlagShipClient;
import com.flagship.starter.client.FlagShipClient;
import com.flagship.starter.client.ResilientFlagShipClient;
import com.flagship.starter.config.FlagshipStarterProperties;
import com.flagship.starter.sync.DiskPersistenceManager;
import com.flagship.starter.sync.FlagshipSyncEngine;
import io.micrometer.core.instrument.MeterRegistry;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Spring Boot AutoConfiguration for FlagShip starter.
 */
@AutoConfiguration
@EnableConfigurationProperties(FlagshipStarterProperties.class)
@ConditionalOnClass(FlagEvaluationEngine.class)
@ConditionalOnProperty(prefix = "flagship", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlagshipAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FlagEvaluationEngine flagEvaluationEngine() {
        return new FlagEvaluationEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public DiskPersistenceManager diskPersistenceManager(FlagshipStarterProperties properties) {
        return new DiskPersistenceManager(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public DefaultFlagShipClient defaultFlagShipClient(
            FlagEvaluationEngine engine,
            DiskPersistenceManager diskPersistenceManager
    ) {
        return new DefaultFlagShipClient(engine, null);
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(FlagShipClient.class)
    public FlagShipClient resilientFlagShipClient(
            DefaultFlagShipClient defaultClient,
            @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        return new ResilientFlagShipClient(defaultClient, meterRegistry);
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean
    public FlagshipSyncEngine flagshipSyncEngine(
            FlagshipStarterProperties properties,
            DefaultFlagShipClient defaultClient,
            DiskPersistenceManager diskPersistenceManager
    ) {
        return new FlagshipSyncEngine(properties, defaultClient, diskPersistenceManager);
    }

    @Bean
    @ConditionalOnClass(Aspect.class)
    @ConditionalOnMissingBean
    public FeatureGuardAspect featureGuardAspect(FlagShipClient flagShipClient) {
        return new FeatureGuardAspect(flagShipClient);
    }

    @Bean
    @ConditionalOnClass(Endpoint.class)
    @ConditionalOnAvailableEndpoint(endpoint = FlagshipEndpoint.class)
    @ConditionalOnMissingBean
    public FlagshipEndpoint flagshipEndpoint(
            FlagShipClient client,
            FlagshipSyncEngine syncEngine,
            FlagshipStarterProperties properties
    ) {
        return new FlagshipEndpoint(client, syncEngine, properties);
    }
}
