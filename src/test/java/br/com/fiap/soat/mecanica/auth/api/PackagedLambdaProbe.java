package br.com.fiap.soat.mecanica.auth.api;

import br.com.fiap.soat.mecanica.auth.application.AuthenticateCustomerUseCase;
import br.com.fiap.soat.mecanica.auth.application.CustomerRepository;
import br.com.fiap.soat.mecanica.auth.domain.Customer;
import br.com.fiap.soat.mecanica.auth.domain.CustomerStatus;
import br.com.fiap.soat.mecanica.auth.infrastructure.DatabaseConfig;
import br.com.fiap.soat.mecanica.auth.infrastructure.JwtTokenIssuer;
import br.com.fiap.soat.mecanica.auth.infrastructure.PostgresCustomerRepository;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Executado em JVM separada, somente com classes de teste e o JAR final. */
public final class PackagedLambdaProbe {
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private static final UUID CUSTOMER_ID = UUID.fromString("b2e5886b-d42f-42ac-a72b-53f230a8baa6");

    public static void main(String[] args) throws Exception {
        var mapper = new ObjectMapper();
        var request = new APIGatewayV2HTTPEvent();
        request.setBody("{\"cpf\":\"00000000000\"}");
        require(new AuthHandler().handleRequest(request, null).getStatusCode() == 400,
                "Handler padrão deve rejeitar CPF inválido sem configuração AWS");

        // Resolve os providers do SDK e JDBC a partir de META-INF/services, sem chamar AWS.
        try (var client = SecretsManagerClient.builder().region(Region.US_EAST_1)
                .credentialsProvider(AnonymousCredentialsProvider.create()).build()) {
            require(client.serviceName().equals("secretsmanager"), "Provider HTTP AWS ausente");
        }
        require(DriverManager.getDriver("jdbc:postgresql://localhost/test") != null, "Driver JDBC ausente");

        CustomerRepository repository = cpf -> Optional.of(new Customer(CUSTOMER_ID, CustomerStatus.ATIVO));
        String port = System.getenv("AUTH_TEST_DB_PORT");
        DatabaseConfig database = null;
        if (port != null) {
            database = new DatabaseConfig("127.0.0.1", Integer.parseInt(port), "auth_review", "auth_review", "");
            repository = new PostgresCustomerRepository(database);
        }
        var issuer = new JwtTokenIssuer(SECRET, "smoke-auth", "smoke-api", Duration.ofHours(1));
        var service = new AuthenticateCustomerUseCase(repository, issuer);
        var handler = new AuthHandler(() -> service, mapper);
        request.setBody("{\"cpf\":\"529.982.247-25\"}");
        var response = handler.handleRequest(request, null);
        require(response.getStatusCode() == 200, "Cliente ativo deve autenticar");
        String token = mapper.readTree(response.getBody()).get("accessToken").asText();
        var jwt = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .requireIssuer("smoke-auth").requireAudience("smoke-api").build().parseSignedClaims(token);
        require(jwt.getHeader().getAlgorithm().equals("HS256"), "Algoritmo incorreto");
        require(jwt.getPayload().getSubject().equals(CUSTOMER_ID.toString()), "Subject incorreto");
        require(jwt.getPayload().get("principal_type").equals("CLIENTE"), "Principal incorreto");
        require(jwt.getPayload().getId() != null, "JTI ausente");
        require(jwt.getPayload().getExpiration().getTime() - jwt.getPayload().getIssuedAt().getTime() == 3600000,
                "Validade incorreta");

        if (database != null) {
            request.setBody("{\"cpf\":\"12345678909\"}");
            require(handler.handleRequest(request, null).getStatusCode() == 401, "Cliente ausente deve ser negado");
            // Altera somente a fixture dedicada criada pelo operador no banco auth_review.
            try (var connection = DriverManager.getConnection(database.jdbcUrl(), database.username(), database.password());
                 var statement = connection.prepareStatement("UPDATE clientes SET status = 'INATIVO' WHERE id = ?")) {
                statement.setObject(1, CUSTOMER_ID);
                statement.executeUpdate();
                try {
                    request.setBody("{\"cpf\":\"52998224725\"}");
                    require(handler.handleRequest(request, null).getStatusCode() == 401, "Cliente inativo deve ser negado");
                } finally {
                    try (var restore = connection.prepareStatement("UPDATE clientes SET status = 'ATIVO' WHERE id = ?")) {
                        restore.setObject(1, CUSTOMER_ID);
                        restore.executeUpdate();
                    }
                }
            }
            System.out.println("POSTGRES_AUTH_OK");
        }
        System.out.println("PACKAGED_LAMBDA_OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
