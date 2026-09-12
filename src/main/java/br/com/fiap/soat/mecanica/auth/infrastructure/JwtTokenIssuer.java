package br.com.fiap.soat.mecanica.auth.infrastructure;

import br.com.fiap.soat.mecanica.auth.application.IssuedToken;
import br.com.fiap.soat.mecanica.auth.application.TokenIssuer;
import br.com.fiap.soat.mecanica.auth.domain.Customer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class JwtTokenIssuer implements TokenIssuer {
    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final Duration ttl;
    private final Clock clock;

    public JwtTokenIssuer(String base64Secret, String issuer, String audience, Duration ttl) {
        this(base64Secret, issuer, audience, ttl, Clock.systemUTC());
    }

    JwtTokenIssuer(String base64Secret, String issuer, String audience, Duration ttl, Clock clock) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(Customer customer) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(customer.id().toString())
                .audience().add(audience).and()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("principal_type", "CLIENTE")
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, ttl.toSeconds());
    }
}
