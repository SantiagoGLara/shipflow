package com.shipflow.events;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

@Document(collection = "workflow_events")
public class WorkflowEventDocument {
    @Id private String eventId;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private Map<String, Object> payload;
    private Instant occurredAt;
    private Instant storedAt;
    public WorkflowEventDocument() {}
    public String getEventId(){return eventId;} public void setEventId(String v){eventId=v;}
    public String getAggregateType(){return aggregateType;} public void setAggregateType(String v){aggregateType=v;}
    public Long getAggregateId(){return aggregateId;} public void setAggregateId(Long v){aggregateId=v;}
    public String getEventType(){return eventType;} public void setEventType(String v){eventType=v;}
    public Map<String,Object> getPayload(){return payload;} public void setPayload(Map<String,Object> v){payload=v;}
    public Instant getOccurredAt(){return occurredAt;} public void setOccurredAt(Instant v){occurredAt=v;}
    public Instant getStoredAt(){return storedAt;} public void setStoredAt(Instant v){storedAt=v;}
}
