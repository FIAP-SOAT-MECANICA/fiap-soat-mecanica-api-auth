package br.com.fiap.soat.mecanica.auth.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class SecretConfigLoader {
    private final ObjectMapper objectMapper;

    public SecretConfigLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DatabaseConfig databaseConfig(String secret) {
        JsonNode root = readJson(secret);
        return new DatabaseConfig(
                required(root, "host"),
                root.path("port").asInt(5432),
                required(root, "dbname"),
                required(root, "username"),
                required(root, "password")
        );
    }

    public String jwtSecret(String secret) {
        JsonNode root = readJson(secret);
        return required(root, "secret");
    }

    private JsonNode readJson(String secret) {
        try {
            return objectMapper.readTree(secret);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Formato de segredo inválido", exception);
        }
    }

    private String required(JsonNode root, String field) {
        String value = root.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Campo obrigatório ausente no segredo: " + field);
        }
        return value;
    }
}
