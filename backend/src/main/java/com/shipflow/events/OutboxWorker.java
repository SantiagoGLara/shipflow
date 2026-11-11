
package com.shipflow.events;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.jdbc.core.JdbcTemplate;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "shipflow.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxWorker {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxWorker.class);

    private final JdbcTemplate jdbc;
    private final MongoTemplate mongo;
    private final ObjectMapper mapper;
    private final int batchSize;
    private final int maxAttempts;
    private final TransactionTemplate tx;

    private volatile boolean indexReady = false;

    public OutboxWorker(
            JdbcTemplate jdbc,
            MongoTemplate mongo,
            ObjectMapper mapper,
            @Value("${shipflow.outbox.batch-size:25}") int batchSize,
            @Value("${shipflow.outbox.max-attempts:8}") int maxAttempts,
            PlatformTransactionManager tm
    ) {
        this.jdbc = jdbc;
        this.mongo = mongo;
        this.mapper = mapper;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.tx = new TransactionTemplate(tm);
    }

    @jakarta.annotation.PostConstruct
    public void ensureIndex() {
        try {
            // createIndex también crea la colección si todavía no existe.
            // El índice único garantiza que un evento no se duplique al reintentar.
            mongo.getCollection("workflow_events").createIndex(
                    new Document("eventId", 1),
                    new IndexOptions().unique(true)
            );

            indexReady = true;
            log.info("MongoDB disponible; índice único eventId verificado");
        } catch (Exception e) {
            indexReady = false;
            log.warn("MongoDB no disponible; Outbox reintentará: {}", e.toString());
        }
    }

    @Scheduled(fixedDelayString = "${shipflow.outbox.poll-ms:3000}")
    public void process() {

        if (!indexReady) {
            ensureIndex();
            if (!indexReady) return;
        }

        recoverAbandonedClaims();

        for (Map<String, Object> e : claim()) {

            Long id = ((Number) e.get("id")).longValue();

            try {
                Map<String, Object> payload = mapper.readValue(
                        String.valueOf(e.get("payload")),
                        new TypeReference<Map<String, Object>>() {}
                );

                String eventId = id.toString();

                Query query = Query.query(
                        Criteria.where("eventId").is(eventId)
                );

                Update update = new Update()
                        .setOnInsert("eventId", eventId)
                        .setOnInsert("aggregateType", e.get("aggregate_type"))
                        .setOnInsert("aggregateId",
                                ((Number) e.get("aggregate_id")).longValue())
                        .setOnInsert("eventType", e.get("event_type"))
                        .setOnInsert("payload", payload)
                        .setOnInsert("occurredAt", e.get("created_at"))
                        .setOnInsert("storedAt", Instant.now());

                mongo.upsert(query, update, "workflow_events");

                jdbc.update(
                        """
                        UPDATE outbox_events
                        SET status='PUBLISHED',
                            processed_at=now(),
                            claimed_at=NULL,
                            last_error=NULL
                        WHERE id=?
                        """,
                        id
                );

            } catch (Exception ex) {

                int attempts =
                        ((Number) e.get("attempts")).intValue() + 1;

                String status =
                        attempts >= maxAttempts ? "FAILED" : "PENDING";

                long delay = Math.min(
                        300L,
                        1L << Math.min(attempts, 8)
                );

                String error = ex.toString();
                error = error.substring(0, Math.min(2000, error.length()));

                jdbc.update(
                        """
                        UPDATE outbox_events
                        SET status=?,
                            attempts=?,
                            last_error=?,
                            claimed_at=NULL,
                            next_attempt_at=now() + (? * interval '1 second')
                        WHERE id=?
                        """,
                        status, attempts, error, delay, id
                );

                log.warn(
                        "Outbox event {} falló (intento {}): {}",
                        id, attempts, ex.toString()
                );
            }
        }
    }

    private void recoverAbandonedClaims() {
        int recovered = jdbc.update(
                """
                UPDATE outbox_events
                SET status='PENDING', claimed_at=NULL,
                    next_attempt_at=now()
                WHERE status='PROCESSING'
                  AND (claimed_at IS NULL
                       OR claimed_at < now() - (? * interval '1 millisecond'))
                """,
                300000
        );
        if (recovered > 0) {
            log.warn("Outbox recuperó {} eventos PROCESSING abandonados", recovered);
        }
    }

    private List<Map<String, Object>> claim() {

        return tx.execute(status -> {

            List<Map<String, Object>> rows = jdbc.queryForList(
                    """
                    SELECT id, aggregate_type, aggregate_id,
                           event_type, payload::text AS payload,
                           created_at, attempts
                    FROM outbox_events
                    WHERE status='PENDING'
                      AND (next_attempt_at IS NULL
                           OR next_attempt_at <= now())
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                    """,
                    batchSize
            );

            for (Map<String, Object> row : rows) {
                jdbc.update(
                        "UPDATE outbox_events SET status='PROCESSING', claimed_at=now() WHERE id=?",
                        row.get("id")
                );
            }

            return rows;
        });
    }
}
