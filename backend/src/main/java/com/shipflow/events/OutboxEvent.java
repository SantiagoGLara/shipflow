package com.shipflow.events;
import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.ColumnTransformer;
@Entity @Table(name="outbox_events")
public class OutboxEvent {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="aggregate_type",nullable=false,length=100) private String aggregateType;
 @Column(name="aggregate_id",nullable=false) private Long aggregateId;
 @Column(name="event_type",nullable=false,length=120) private String eventType;
 @Column(nullable=false,columnDefinition="jsonb")
 @ColumnTransformer(write="?::jsonb")
 private String payload;
 @Column(nullable=false,length=20) private String status="PENDING";
 @Column(nullable=false) private int attempts=0;
 @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
 @Column(name="processed_at") private Instant processedAt;
 @Column(name="next_attempt_at") private Instant nextAttemptAt;
 @Column(name="last_error",columnDefinition="TEXT") private String lastError;
 public OutboxEvent() {}
 public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) { this.aggregateType=aggregateType; this.aggregateId=aggregateId; this.eventType=eventType; this.payload=payload; }
 public Long getId(){return id;} public String getAggregateType(){return aggregateType;} public Long getAggregateId(){return aggregateId;} public String getEventType(){return eventType;} public String getPayload(){return payload;} public String getStatus(){return status;} public int getAttempts(){return attempts;} public Instant getCreatedAt(){return createdAt;} public Instant getProcessedAt(){return processedAt;} public Instant getNextAttemptAt(){return nextAttemptAt;} public String getLastError(){return lastError;}
}
