package com.shipflow.outbox;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;

@Document(collection = "shipflow_events")
public class WorkflowEventDocument {
    @Id
    private String id;
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Map<String, Object> payload;
    private Instant occurredAt;
    private Instant storedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getStoredAt() { return storedAt; }
    public void setStoredAt(Instant storedAt) { this.storedAt = storedAt; }
}
