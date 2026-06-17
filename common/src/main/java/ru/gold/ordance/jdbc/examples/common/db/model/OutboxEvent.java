package ru.gold.ordance.jdbc.examples.common.db.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class OutboxEvent {

    private UUID eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String payload;
    private OffsetDateTime createdAt;
    private OffsetDateTime processedAt;
    private String status;
    private OffsetDateTime claimedAt;
    private OffsetDateTime claimUntil;
    private int attemptCount;
    private String lastError;
    private OffsetDateTime nextAttemptAt;

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public void setAggregateType(String aggregateType) {
        this.aggregateType = aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(String aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(OffsetDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public OffsetDateTime getClaimUntil() {
        return claimUntil;
    }

    public void setClaimUntil(OffsetDateTime claimUntil) {
        this.claimUntil = claimUntil;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    @Override
    public String toString() {
        return "OutboxEvent{" +
                "eventId=" + eventId +
                ", aggregateType='" + aggregateType + '\'' +
                ", aggregateId='" + aggregateId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", payload='" + payload + '\'' +
                ", createdAt=" + createdAt +
                ", processedAt=" + processedAt +
                ", status='" + status + '\'' +
                ", claimedAt=" + claimedAt +
                ", claimUntil=" + claimUntil +
                ", attemptCount=" + attemptCount +
                ", lastError='" + lastError + '\'' +
                ", nextAttemptAt=" + nextAttemptAt +
                '}';
    }
}
