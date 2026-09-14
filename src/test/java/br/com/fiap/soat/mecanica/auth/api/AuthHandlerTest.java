package br.com.fiap.soat.mecanica.auth.api;

import br.com.fiap.soat.mecanica.auth.application.AuthenticateCustomerUseCase;
import br.com.fiap.soat.mecanica.auth.application.IssuedToken;
import br.com.fiap.soat.mecanica.auth.domain.Customer;
import br.com.fiap.soat.mecanica.auth.domain.CustomerStatus;
import br.com.fiap.soat.mecanica.auth.infrastructure.CustomerLookupException;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthHandlerTest {
    @Test
    void rejectsOversizedBodyBeforeInitializingServices() {
        var handler = new AuthHandler(() -> { throw new AssertionError("Servico nao deve iniciar"); }, new ObjectMapper());
        assertEquals(400, handler.handleRequest(event(" ".repeat(4096) + "{}", null), null).getStatusCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "[]", "{", "{\"cpf\":52998224725}",
            "{\"cpf\":\"52998224725\",\"extra\":true}", "{\"cpf\":\"52998224725\"} {}",
            "{\"cpf\":\"00000000000\",\"cpf\":\"52998224725\"}"})
    void rejectsMalformedRequestsBeforeInitializingServices(String body) {
        AuthHandler handler = new AuthHandler(() -> {
            throw new AssertionError("Serviços não devem inicializar para requisição inválida");
        }, new ObjectMapper());

        var response = handler.handleRequest(event(body, null), null);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("INVALID_REQUEST"));
    }

    @Test
    void validatesCpfBeforeInitializingServices() {
        AuthHandler handler = new AuthHandler(() -> {
            throw new AssertionError("Serviços não devem inicializar para CPF inválido");
        }, new ObjectMapper());

        var response = handler.handleRequest(event("{\"cpf\":\"00000000000\"}", null), null);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("INVALID_CPF"));
    }

    @Test
    void decodesBase64RequestBody() {
        AuthHandler handler = new AuthHandler(() -> new AuthenticateCustomerUseCase(
                cpf -> Optional.of(new Customer(UUID.randomUUID(), CustomerStatus.ATIVO)),
                customer -> new IssuedToken("signed-token", 3600)), new ObjectMapper());
        var request = event(Base64.getEncoder().encodeToString(
                "{\"cpf\":\"52998224725\"}".getBytes(StandardCharsets.UTF_8)), null);
        request.setIsBase64Encoded(true);

        assertEquals(200, handler.handleRequest(request, null).getStatusCode());
    }

    @Test
    void rejectsInvalidBase64() {
        AuthHandler handler = new AuthHandler(() -> {
            throw new AssertionError("Serviços não devem inicializar para Base64 inválido");
        }, new ObjectMapper());
        var request = event("%%%", null);
        request.setIsBase64Encoded(true);

        assertEquals(400, handler.handleRequest(request, null).getStatusCode());
    }

    @Test
    void treatsServiceConfigurationFailuresAsInternalErrors() {
        AuthHandler handler = new AuthHandler(() -> {
            throw new IllegalArgumentException("configuração interna inválida");
        }, new ObjectMapper());

        var response = handler.handleRequest(event("{\"cpf\":\"52998224725\"}", null), null);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("INTERNAL_ERROR"));
    }

    @Test
    void returnsUnavailableWithoutLeakingDatabaseFailureDetails() {
        AuthHandler handler = new AuthHandler(() -> new AuthenticateCustomerUseCase(cpf -> {
            throw new CustomerLookupException(new SQLException("private-connection-details"));
        }, customer -> { throw new AssertionError("Token não deve ser emitido"); }), new ObjectMapper());

        var response = handler.handleRequest(event("{\"cpf\":\"52998224725\"}", "db-failure"), null);

        assertEquals(503, response.getStatusCode());
        assertTrue(response.getBody().contains("CUSTOMER_DIRECTORY_UNAVAILABLE"));
        assertTrue(!response.getBody().contains("private-connection-details"));
    }

    @Test
    void returnsTokenAndPropagatesValidCorrelationId() {
        AuthenticateCustomerUseCase service = new AuthenticateCustomerUseCase(
                cpf -> Optional.of(new Customer(UUID.randomUUID(), CustomerStatus.ATIVO)),
                customer -> new IssuedToken("signed-token", 3600)
        );
        AuthHandler handler = new AuthHandler(() -> service, new ObjectMapper());

        var response = handler.handleRequest(event("{\"cpf\":\"529.982.247-25\"}", "demo-123"), null);

        assertEquals(200, response.getStatusCode());
        assertEquals("demo-123", response.getHeaders().get("x-correlation-id"));
        assertTrue(response.getBody().contains("signed-token"));
        assertEquals("no-store", response.getHeaders().get("Cache-Control"));
    }

    @Test
    void usesApiGatewayRequestIdWhenCorrelationHeaderIsAbsent() {
        AuthenticateCustomerUseCase service = new AuthenticateCustomerUseCase(
                cpf -> Optional.of(new Customer(UUID.randomUUID(), CustomerStatus.ATIVO)),
                customer -> new IssuedToken("signed-token", 3600)
        );
        AuthHandler handler = new AuthHandler(() -> service, new ObjectMapper());
        APIGatewayV2HTTPEvent event = event("{\"cpf\":\"52998224725\"}", null);
        APIGatewayV2HTTPEvent.RequestContext requestContext = new APIGatewayV2HTTPEvent.RequestContext();
        requestContext.setRequestId("api-gateway-request-42");
        event.setRequestContext(requestContext);

        var response = handler.handleRequest(event, null);

        assertEquals(200, response.getStatusCode());
        assertEquals("api-gateway-request-42", response.getHeaders().get("x-correlation-id"));
    }

    @Test
    void rejectsInvalidCpfWithoutCallingTheCustomerRepository() {
        AuthHandler handler = new AuthHandler(
                () -> new AuthenticateCustomerUseCase(
                        cpf -> {
                            throw new AssertionError("Consulta não deveria ocorrer para CPF inválido");
                        },
                        customer -> new IssuedToken("unused", 3600)
                ),
                new ObjectMapper()
        );

        var response = handler.handleRequest(event("{\"cpf\":\"000.000.000-00\"}", null), null);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("INVALID_CPF"));
    }

    @Test
    void givesTheSameUnauthorizedResponseForInactiveCustomer() {
        AuthenticateCustomerUseCase service = new AuthenticateCustomerUseCase(
                cpf -> Optional.of(new Customer(UUID.randomUUID(), CustomerStatus.INATIVO)),
                customer -> new IssuedToken("unused", 3600)
        );
        AuthHandler handler = new AuthHandler(() -> service, new ObjectMapper());

        var response = handler.handleRequest(event("{\"cpf\":\"52998224725\"}", null), null);

        assertEquals(401, response.getStatusCode());
        assertTrue(response.getBody().contains("ACCESS_DENIED"));
        assertTrue(response.getBody().contains("correlationId"));
    }

    private APIGatewayV2HTTPEvent event(String body, String correlationId) {
        APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
        event.setBody(body);
        if (correlationId != null) {
            event.setHeaders(Map.of("X-Correlation-Id", correlationId));
        }
        return event;
    }
}
