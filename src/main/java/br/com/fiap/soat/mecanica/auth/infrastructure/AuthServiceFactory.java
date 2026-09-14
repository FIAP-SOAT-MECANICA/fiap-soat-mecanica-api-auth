package br.com.fiap.soat.mecanica.auth.infrastructure;

import br.com.fiap.soat.mecanica.auth.application.AuthenticateCustomerUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

public final class AuthServiceFactory {
    private final Map<String, String> environment;
    private final SecretReader secretReader;
    private final SecretConfigLoader configLoader;
    private AuthenticateCustomerUseCase cached;
    private final java.time.Clock clock;
    private java.time.Instant expiresAt = java.time.Instant.MIN;

    public AuthServiceFactory() {
        this(System.getenv(), new AwsSecretsManagerReader(), new SecretConfigLoader(new ObjectMapper()));
    }

    AuthServiceFactory(Map<String, String> environment, SecretReader secretReader, SecretConfigLoader configLoader) {
        this(environment, secretReader, configLoader, java.time.Clock.systemUTC());
    }

    AuthServiceFactory(Map<String, String> environment, SecretReader secretReader, SecretConfigLoader configLoader,
                       java.time.Clock clock) {
        this.environment = environment;
        this.secretReader = secretReader;
        this.configLoader = configLoader;
        this.clock = clock;
    }

    public synchronized AuthenticateCustomerUseCase get() {
        if (cached == null || !clock.instant().isBefore(expiresAt)) {
            DatabaseConfig database = configLoader.databaseConfig(secretReader.read(required("DB_SECRET_ARN")));
            String jwtSecret = configLoader.jwtSecret(secretReader.read(required("JWT_SECRET_ARN")));
            cached = new AuthenticateCustomerUseCase(
                    new PostgresCustomerRepository(database),
                    new JwtTokenIssuer(
                            jwtSecret,
                            environment.getOrDefault("JWT_ISSUER", "fiap-soat-mecanica-auth"),
                            required("JWT_AUDIENCE"),
                            Duration.ofSeconds(Long.parseLong(environment.getOrDefault("JWT_TTL_SECONDS", "3600")))
                    )
            );
            expiresAt = clock.instant().plusSeconds(300);
        }
        return cached;
    }

    private String required(String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória ausente: " + name);
        }
        return value;
    }
}
