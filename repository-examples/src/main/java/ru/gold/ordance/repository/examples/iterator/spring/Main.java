package ru.gold.ordance.repository.examples.iterator.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import ru.gold.ordance.repository.examples.iterator.UserRepository;

@SpringBootApplication
public class Main {

    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Main.class);
        application.setAdditionalProfiles("iterator");
        application.run(args);
    }

    @Bean
    @Profile("!test")
    ApplicationRunner iteratorExampleRunner(UserRepository userRepository) {
        return args -> userRepository.iterator().forEachRemaining(user -> LOGGER.info("Found user: {}", user));
    }
}
