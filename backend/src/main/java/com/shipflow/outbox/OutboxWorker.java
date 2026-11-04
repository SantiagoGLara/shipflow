package com.shipflow.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Component
public class OutboxWorker {
    private static final Logger log = LoggerFactory.getLogger(OutboxWorker.class);
    private final JdbcTemplate jdbc;
    private final MongoTemplate mongo;
    private final ObjectMapper mapper;
    private final int batchSize;
    private final int maxAttempts;
    private final int staleSeconds;
    private final TransactionTemplate transactionTemplate;
    private volatile boolean mongoIndexReady = false;

    public OutboxWorker(JdbcTemplate jdbc, MongoTemplate mongo, ObjectMapper mapper,
            @Value("${shipflow.outbox.batch-size:25}") int batchSize,
            @Value("${shipflow.outbox.max-attempts:8}") int maxAttempts,
            @Value("${shipflow.outbox.stale-seconds:120}") int staleSeconds,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.mongo = mongo;
        this.mapper = mapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.staleSeconds = Math.max(30, staleSeconds);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @jakarta.annotation.PostConstruct
    public void ensureIndexes() {
        try {
            mongo.indexOps(WorkflowEventDocument.class)
                .ensureIndex(new Index().on("eventId", org.springframework.data.domain.Sort.Direction.ASC).unique());
            mongoIndexReady = true;
        } catch (Exception ex) {
            // MongoDB no debe impedir que Spring Boot arranque.
            log.warn("MongoDB no disponible al crear índice Outbox; se reintentará: {}", ex.toString());
        }
    }

    @Scheduled(fixedDelayString = "${shipflow.outbox.poll-ms:3000}")
    public void processPendingEvents() {
        recoverStaleProcessing();
        if (!mongoIndexReady) {
            ensureIndexes();
            if (!mongoIndexReady) return;
        }
        List<Map<String, Object>> batch = claimBatch();
        for (Map<String, Object> event : batch) {
            UUID id = (UUID) event.get("id");
            try {
                Map<String, Object> payload = mapper.readValue(
                    String.valueOf(event.get("payload")), new TypeReference<Map<String, Object>>() {});
                Query query = Query.query(Criteria.where("eventId").is(id.toString()));
                Update update = new Update()
                    .setOnInsert("eventId", id.toString())
                    .setOnInsert("aggregateType", event.get("aggregate_type"))
                    .setOnInsert("aggregateId", event.get("aggregate_id"))
                    .setOnInsert("eventType", event.get("event_type"))
                    .setOnInsert("payload", payload)
                    .setOnInsert("occurredAt", event.get("created_at"))
                    .setOnInsert("storedAt", Instant.now());
                mongo.upsert(query, update, WorkflowEventDocument.class);
                jdbc.update("UPDATE outbox_events SET status='PROCESSED', processed_at=now(), processing_started_at=NULL, last_error=NULL WHERE id=?", id);
            } catch (Exception ex) {
                int attempts = ((Number) event.get("attempts")).intValue() + 1;
                String status = attempts >= maxAttempts ? "DEAD" : "PENDING";
                long delaySeconds = Math.min(300L, 1L << Math.min(attempts, 8));
                jdbc.update("""
                    UPDATE outbox_events
                    SET status=?, attempts=?, last_error=?, processing_started_at=NULL, next_attempt_at=now() + (? * interval '1 second')
                    WHERE id=?
                    """, status, attempts, abbreviate(ex.toString(), 2000), delaySeconds, id);
                log.warn("Outbox event {} failed (attempt {}): {}", id, attempts, ex.toString());
            }
        }
    }


    /** Devuelve a PENDING los eventos cuyo worker murió o quedó detenido. */
    protected void recoverStaleProcessing() {
        int recovered = jdbc.update("""
            UPDATE outbox_events
            SET status='PENDING', processing_started_at=NULL,
                next_attempt_at=now(),
                last_error=concat_ws(' | ', nullif(last_error,''), 'Recuperado tras timeout de procesamiento')
            WHERE status='PROCESSING'
              AND processing_started_at < now() - (? * interval '1 second')
            """, staleSeconds);
        if (recovered > 0) log.warn("Outbox: {} evento(s) recuperado(s) desde PROCESSING", recovered);
    }

    protected List<Map<String, Object>> claimBatch() {
        return transactionTemplate.execute(status -> {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT id, aggregate_type, aggregate_id, event_type, payload::text AS payload,
                   created_at, attempts
            FROM outbox_events
            WHERE status='PENDING' AND next_attempt_at <= now()
            ORDER BY created_at
            FOR UPDATE SKIP LOCKED
            LIMIT ?
            """, batchSize);
        for (Map<String, Object> row : rows) {
            jdbc.update("UPDATE outbox_events SET status='PROCESSING', processing_started_at=now() WHERE id=?", row.get("id"));
        }
        return rows;
        });
    }

    private String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
