package com.flagship.server;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Testcontainers PostgreSQL integration test running the full FlagShip Server control plane suite
 * against a real {@code postgres:16-alpine} container when Docker is available (e.g. GitHub Actions CI) (C5).
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresTestcontainersIntegrationTest extends FlagshipServerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("flagship_tc")
            .withUsername("flagship")
            .withPassword("flagship_tc_secret");
}
