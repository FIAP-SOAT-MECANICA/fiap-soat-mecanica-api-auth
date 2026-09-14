package br.com.fiap.soat.mecanica.auth.infrastructure;

import br.com.fiap.soat.mecanica.auth.domain.Cpf;
import br.com.fiap.soat.mecanica.auth.domain.InvalidCpfException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurationTest {
    private static final String DB = """
            {"host":"database.rds.amazonaws.com","port":5432,"dbname":"mecanica","username":"fixture","password":"fixture-only"}
            """;
    private final SecretConfigLoader loader = new SecretConfigLoader(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"abc52998224725", "52998224725xyz", "529 982 247 25", "529-982.247/25"})
    void rejectsNonContractCpfFormats(String cpf) {
        assertThrows(InvalidCpfException.class, () -> new Cpf(cpf));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{", "{\"secret\":42}", "{\"secret\":\"a\",\"secret\":\"b\"}", "{\"secret\":\"a\"} {}"})
    void rejectsInvalidSecrets(String secret) {
        assertThrows(IllegalStateException.class, () -> loader.jwtSecret(secret));
    }

    @Test
    void dbContractRequiresTlsAndBoundsWaitingWithoutPrintingCredentials() {
        DatabaseConfig database = loader.databaseConfig(DB);
        assertTrue(database.jdbcUrl().contains("sslmode=verify-full"));
        assertTrue(java.nio.file.Files.exists(RdsCertificate.path()));
        assertTrue(database.jdbcUrl().contains("connectTimeout=3"));
        assertTrue(database.jdbcUrl().contains("socketTimeout=5"));
        assertFalse(database.toString().contains("fixture"));
        assertThrows(IllegalStateException.class, () -> loader.databaseConfig(DB.replace("5432", "\"broken\"")));
        assertThrows(IllegalArgumentException.class, () -> loader.databaseConfig(DB.replace("5432", "0")));
        assertThrows(IllegalArgumentException.class, () -> loader.databaseConfig(DB.replace("mecanica", "mecanica?sslmode=disable")));
    }

    @Test
    void secretsRefreshAfterFiveMinutesAndFailureDoesNotExtendStaleCache() {
        AtomicInteger reads = new AtomicInteger();
        var clock = new MutableClock();
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        SecretReader reader = arn -> {
            int count = reads.incrementAndGet();
            if (count == 3) throw new IllegalStateException("fixture failure");
            return arn.equals("db") ? DB : "{\"secret\":\"" + key + "\"}";
        };
        var factory = new AuthServiceFactory(Map.of("DB_SECRET_ARN", "db", "JWT_SECRET_ARN", "jwt", "JWT_AUDIENCE", "api"), reader, loader, clock);
        var initial = factory.get();
        assertSame(initial, factory.get());
        assertEquals(2, reads.get());
        clock.now = clock.now.plusSeconds(300);
        assertThrows(IllegalStateException.class, factory::get);
        assertNotSame(initial, factory.get());
        assertEquals(5, reads.get());
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-14T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
