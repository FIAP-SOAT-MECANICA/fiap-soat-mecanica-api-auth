package br.com.fiap.soat.mecanica.auth.infrastructure;

import br.com.fiap.soat.mecanica.auth.application.IssuedToken;
import br.com.fiap.soat.mecanica.auth.domain.Customer;
import br.com.fiap.soat.mecanica.auth.domain.CustomerStatus;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtTokenIssuerTest {
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    void createsAClientJwtWithTheCustomerUuidAsSubject() {
        UUID customerId = UUID.fromString("b2e5886b-d42f-42ac-a72b-53f230a8baa6");
        Instant now = Instant.parse("2026-09-11T12:00:00Z");
        JwtTokenIssuer issuer = new JwtTokenIssuer(
                SECRET,
                "fiap-soat-mecanica-auth",
                "fiap-soat-mecanica-api",
                Duration.ofHours(1),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        IssuedToken token = issuer.issue(new Customer(customerId, CustomerStatus.ATIVO));
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .clock(() -> Date.from(now))
                .build()
                .parseSignedClaims(token.accessToken())
                .getPayload();

        assertEquals(customerId.toString(), claims.getSubject());
        assertEquals("fiap-soat-mecanica-auth", claims.getIssuer());
        assertEquals("CLIENTE", claims.get("principal_type"));
        assertEquals(3600, token.expiresInSeconds());
    }
}
