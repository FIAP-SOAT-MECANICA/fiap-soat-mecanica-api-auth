package br.com.fiap.soat.mecanica.auth.api;

import br.com.fiap.soat.mecanica.auth.application.AccessDeniedException;
import br.com.fiap.soat.mecanica.auth.application.AuthenticateCustomerUseCase;
import br.com.fiap.soat.mecanica.auth.application.IssuedToken;
import br.com.fiap.soat.mecanica.auth.domain.Cpf;
import br.com.fiap.soat.mecanica.auth.domain.InvalidCpfException;
import br.com.fiap.soat.mecanica.auth.infrastructure.AuthServiceFactory;
import br.com.fiap.soat.mecanica.auth.infrastructure.CustomerLookupException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class AuthHandler implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {
    private static final String CORRELATION_HEADER = "x-correlation-id";

    private final Supplier<AuthenticateCustomerUseCase> authService;
    private final ObjectMapper objectMapper;
    private final StructuredLogger logger;

    public AuthHandler() {
        this(new AuthServiceFactory()::get, defaultMapper());
    }

    AuthHandler(Supplier<AuthenticateCustomerUseCase> authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.logger = new StructuredLogger(objectMapper);
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String correlationId = correlationId(event, context);
        Cpf cpf;
        try {
            cpf = new Cpf(parseRequest(event).cpf());
        } catch (InvalidCpfException exception) {
            return error(400, "INVALID_CPF", "CPF inválido", correlationId);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            return error(400, "INVALID_REQUEST", "Corpo da requisição inválido", correlationId);
        }
        try {
            IssuedToken token = authService.get().execute(cpf.value());
            logger.info("auth_succeeded", correlationId, Map.of("principalType", "CLIENTE"));
            return response(200, new TokenResponse(token.accessToken(), "Bearer", token.expiresInSeconds()), correlationId);
        } catch (AccessDeniedException exception) {
            logger.info("auth_denied", correlationId, Map.of("reason", "customer_not_eligible"));
            return error(401, "ACCESS_DENIED", "Não foi possível autenticar o cliente", correlationId);
        } catch (CustomerLookupException exception) {
            logger.info("customer_lookup_unavailable", correlationId, Map.of());
            return error(503, "CUSTOMER_DIRECTORY_UNAVAILABLE", "Serviço de autenticação indisponível", correlationId);
        } catch (Exception exception) {
            logger.info("auth_unexpected_failure", correlationId, Map.of("exception", exception.getClass().getSimpleName()));
            return error(500, "INTERNAL_ERROR", "Erro interno inesperado", correlationId);
        }
    }

    private AuthRequest parseRequest(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
        if (event == null || event.getBody() == null || event.getBody().isBlank()) {
            throw new IllegalArgumentException("Corpo ausente");
        }
        String body = event.getBody();
        if (event.getIsBase64Encoded()) {
            body = new String(Base64.getDecoder().decode(body), StandardCharsets.UTF_8);
        }
        JsonNode root = objectMapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(body);
        if (root == null || !root.isObject() || !root.has("cpf") || root.size() != 1
                || !root.get("cpf").isTextual()) {
            throw new IllegalArgumentException("Esperado objeto com CPF textual");
        }
        return new AuthRequest(root.get("cpf").textValue());
    }

    private APIGatewayV2HTTPResponse error(int status, String code, String message, String correlationId) {
        logger.info("auth_failed", correlationId, Map.of("code", code, "httpStatus", status));
        return response(status, new ErrorResponse(code, message, correlationId), correlationId);
    }

    private APIGatewayV2HTTPResponse response(int status, Object body, String correlationId) {
        try {
            APIGatewayV2HTTPResponse response = new APIGatewayV2HTTPResponse();
            response.setStatusCode(status);
            response.setHeaders(Map.of(
                    "Content-Type", "application/json; charset=utf-8",
                    CORRELATION_HEADER, correlationId,
                    "Cache-Control", "no-store"
            ));
            response.setBody(objectMapper.writeValueAsString(body));
            return response;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Falha ao serializar resposta", exception);
        }
    }

    private String correlationId(APIGatewayV2HTTPEvent event, Context context) {
        if (event != null && event.getHeaders() != null) {
            String incoming = event.getHeaders().entrySet().stream()
                    .filter(entry -> CORRELATION_HEADER.equalsIgnoreCase(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .filter(value -> value != null && value.matches("[A-Za-z0-9._-]{1,128}"))
                    .findFirst()
                    .orElse(null);
            if (incoming != null) {
                return incoming;
            }
        }
        if (context != null && context.getAwsRequestId() != null && !context.getAwsRequestId().isBlank()) {
            return context.getAwsRequestId();
        }
        return UUID.randomUUID().toString();
    }

    private static ObjectMapper defaultMapper() {
        return new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
