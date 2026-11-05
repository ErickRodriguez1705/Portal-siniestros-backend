package com.portalsiniestros.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {
    private final Key key;
    public JwtService(@Value("${app.jwt.secret}") String secret) {
        this.key = secret.isBlank() ? Keys.secretKeyFor(SignatureAlgorithm.HS256)
                : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
    public String create(String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder().setSubject(email).setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 86_400_000L))
                .signWith(key, SignatureAlgorithm.HS256).compact();
    }
    public String email(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
        return claims.getSubject();
    }
}
