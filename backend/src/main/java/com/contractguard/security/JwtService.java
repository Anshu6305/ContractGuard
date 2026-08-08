package com.contractguard.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * Creates and validates JSON Web Tokens.
 *
 * A JWT has three dot-separated parts: header.payload.signature. The payload is
 * only Base64-encoded, NOT encrypted -- anyone can read it. What prevents
 * tampering is the signature, computed with a server-side secret. If the payload
 * is edited the signature no longer matches and parsing throws.
 *
 * Tokens here are stateless: there is no server-side session, so any instance
 * can serve any request. The trade-off is that a token cannot be revoked before
 * it expires, which is why the expiry is kept short.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${contractguard.jwt.secret}") String secret,
            @Value("${contractguard.jwt.expiration-ms}") long expirationMs) {
        // HMAC-SHA256 requires a key of at least 256 bits (32 bytes).
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String expectedEmail) {
        try {
            String email = extractEmail(token);
            return email.equals(expectedEmail) && !isExpired(token);
        } catch (Exception ex) {
            // Signature mismatch, malformed token, expired token -- all invalid.
            return false;
        }
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
