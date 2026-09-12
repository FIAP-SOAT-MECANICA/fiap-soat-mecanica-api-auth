package br.com.fiap.soat.mecanica.auth.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

final class StructuredLogger {
    private final ObjectMapper objectMapper;

    StructuredLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void info(String event, String correlationId, Map<String, Object> fields) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("timestamp", Instant.now().toString());
        message.put("level", "INFO");
        message.put("event", event);
        message.put("correlationId", correlationId);
        message.putAll(fields);
        try {
            System.out.println(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            System.out.println("{\"level\":\"ERROR\",\"event\":\"log_serialization_failure\"}");
        }
    }
}
