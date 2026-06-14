package ru.gold.ordance.jdbc.examples.testcontainers;

import org.flywaydb.core.Flyway;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;

import static org.testcontainers.utility.DockerImageName.parse;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersPostgresConfiguration {

    private static final String POSTGRES = "postgres:17-alpine";
    private static final String POSTGRES_URL = "spring.datasource.url";
    private static final String POSTGRES_USERNAME = "spring.datasource.username";
    private static final String POSTGRES_PASSWORD = "spring.datasource.password";
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(parse(POSTGRES).asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgresContainer) {
        return properties -> {
            properties.add(POSTGRES_URL, postgresContainer::getJdbcUrl);
            properties.add(POSTGRES_USERNAME, postgresContainer::getUsername);
            properties.add(POSTGRES_PASSWORD, postgresContainer::getPassword);
        };
    }

    @Bean
    Flyway testcontainersFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations(MIGRATION_LOCATION)
                .cleanDisabled(false)
                .load();
    }
}
