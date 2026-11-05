package com.shipflow.events;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper mapper;

    public OutboxService(
            OutboxEventRepository repository,
            ObjectMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public void publishEvent(
            String aggregateType,
            Long aggregateId,
            String eventType,
            Object payload
    ) {
        try {
            String json = mapper.writeValueAsString(payload);

            repository.save(
                new OutboxEvent(
                    aggregateType,
                    aggregateId,
                    eventType,
                    json
                )
            );

        } catch (JacksonException ex) {
            throw new IllegalStateException(
                "No se pudo serializar el evento de auditoría",
                ex
            );
        }
    }
}