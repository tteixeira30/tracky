package com.tracky.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final long TTL_MS = 7L * 24 * 60 * 60 * 1000; // 7 dias

    private final SecretKey key;

    public JwtService(@Value("${tracky.jwt-secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(new Date(now))
                .expiration(new Date(now + TTL_MS))
                .signWith(key)
                .compact();
    }

    /** Devolve o id do utilizador se o token for válido e não tiver expirado. */
    public Optional<Long> validate(String token) {
        try {
            String subject = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Optional.of(Long.parseLong(subject));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
