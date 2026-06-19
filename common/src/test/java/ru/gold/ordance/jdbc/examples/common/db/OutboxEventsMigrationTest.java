package ru.gold.ordance.jdbc.examples.common.db;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventsMigrationTest {

    @Test
    void outboxEvents_claimFields_ShouldMatchProcessingStatus() throws IOException {
        String migration = new String(
                getClass().getResourceAsStream("/db/migration/V1.0.2__create_outbox_events.sql").readAllBytes(),
                StandardCharsets.UTF_8
        );

        assertThat(migration.replaceAll("\\s+", " "))
                .contains("CHECK ( (status = 'PROCESSING' AND claimed_at IS NOT NULL AND claim_until IS NOT NULL) "
                        + "OR (status <> 'PROCESSING' AND claimed_at IS NULL AND claim_until IS NULL) )");
    }
}
