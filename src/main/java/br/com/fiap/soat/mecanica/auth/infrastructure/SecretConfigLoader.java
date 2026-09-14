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
        JsonNode port = root.path("port");
        if (!port.isMissingNode() && (!port.canConvertToInt() || !port.isIntegralNumber())) {
            throw new IllegalStateException("Porta do banco deve ser um inteiro");
        }
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
            JsonNode root = objectMapper.reader()
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readTree(secret);
            if (root == null || !root.isObject()) {
                throw new IllegalStateException("Segredo deve ser um objeto JSON");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Formato de segredo inválido");
        }
    }

    private String required(JsonNode root, String field) {
        String value = root.path(field).isTextual() ? root.path(field).textValue() : null;
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Campo obrigatório ausente no segredo: " + field);
        }
        return value;
    }
}
