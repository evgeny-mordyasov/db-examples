package ru.gold.ordance.jdbc.examples.testcontainers;

import org.flywaydb.core.Flyway;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.function.Supplier;

import static org.testcontainers.utility.DockerImageName.parse;

@Configuration(proxyBeanMethods = false)
public class Containers {

    private static final String POSTGRES = "postgres:17-alpine";
    private static final String PG_CONN_STR = "postgres-addr";
    private static final String MIGRATION_LOCATION = "classpath:db/migration";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(parse(POSTGRES).asCompatibleSubstituteFor("postgres"));
    }

    @Bean
    DynamicPropertyRegistrar postgresProperties(PostgreSQLContainer postgresContainer) {
        return properties -> properties.add(PG_CONN_STR, hostAndFirstMappedPort(postgresContainer));
    }

    private static Supplier<Object> hostAndFirstMappedPort(ContainerState cs) {
        return () -> cs.getHost() + ":" + cs.getFirstMappedPort();
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
