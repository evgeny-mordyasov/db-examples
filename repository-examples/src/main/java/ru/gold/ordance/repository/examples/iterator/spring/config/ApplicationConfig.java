package ru.gold.ordance.repository.examples.iterator.spring.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import ru.gold.ordance.repository.examples.iterator.UserRepository;
import ru.gold.ordance.repository.examples.iterator.UserRepositoryImpl;

@Configuration(proxyBeanMethods = false)
public class ApplicationConfig {

    private static final int USER_REPOSITORY_BATCH_SIZE = 10;

    @Bean
    UserRepository userRepository(JdbcClient jdbcClient) {
        return new UserRepositoryImpl(jdbcClient, USER_REPOSITORY_BATCH_SIZE);
    }
}
