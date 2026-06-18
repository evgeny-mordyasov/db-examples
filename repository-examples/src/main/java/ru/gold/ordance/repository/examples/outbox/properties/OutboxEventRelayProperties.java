package ru.gold.ordance.repository.examples.outbox.properties;

import java.time.Duration;

public class OutboxEventRelayProperties {

    private int maxErrorLength;
    private int maxAttempts;
    private Duration nextAttemptDelay;
    private Duration processingTimeout;

    public int getMaxErrorLength() {
        return maxErrorLength;
    }

    public void setMaxErrorLength(int maxErrorLength) {
        this.maxErrorLength = maxErrorLength;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getNextAttemptDelay() {
        return nextAttemptDelay;
    }

    public void setNextAttemptDelay(Duration nextAttemptDelay) {
        this.nextAttemptDelay = nextAttemptDelay;
    }

    public Duration getProcessingTimeout() {
        return processingTimeout;
    }

    public void setProcessingTimeout(Duration processingTimeout) {
        this.processingTimeout = processingTimeout;
    }
}
