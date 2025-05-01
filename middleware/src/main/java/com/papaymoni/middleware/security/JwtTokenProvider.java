package com.papaymoni.middleware.security;
//
//import io.jsonwebtoken.*;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
//import org.springframework.security.core.Authentication;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.PostConstruct;
//import java.util.Base64;
//import java.util.Date;
//
//@Component
//public class JwtTokenProvider {
//
//    @Value("${app.jwt.secret}")
//    private String jwtSecret;
//
//    @Value("${app.jwt.expiration}")
//    private long jwtExpirationInMs;
//
//    private final CustomUserDetailsService userDetailsService;
//
//    public JwtTokenProvider(CustomUserDetailsService userDetailsService) {
//        this.userDetailsService = userDetailsService;
//    }
//
//    @PostConstruct
//    protected void init() {
//        jwtSecret = Base64.getEncoder().encodeToString(jwtSecret.getBytes());
//    }
//
//    public String generateToken(Authentication authentication) {
//        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
//        Date now = new Date();
//        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);
//
//        return Jwts.builder()
//                .setSubject(userDetails.getUsername())
//                .setIssuedAt(new Date())
//                .setExpiration(expiryDate)
//                .signWith(SignatureAlgorithm.HS512, jwtSecret)
//                .compact();
//    }
//
//    public String getUsernameFromJWT(String token) {
//        Claims claims = Jwts.parser()
//                .setSigningKey(jwtSecret)
//                .parseClaimsJws(token)
//                .getBody();
//
//        return claims.getSubject();
//    }
//
//    public boolean validateToken(String authToken) {
//        try {
//            Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
//            return true;
//        } catch (SignatureException ex) {
//            // Invalid JWT signature
//        } catch (MalformedJwtException ex) {
//            // Invalid JWT token
//        } catch (ExpiredJwtException ex) {
//            // Expired JWT token
//        } catch (UnsupportedJwtException ex) {
//            // Unsupported JWT token
//        } catch (IllegalArgumentException ex) {
//            // JWT claims string is empty
//        }
//        return false;
//    }
//
//    public Authentication getAuthentication(String token) {
//        UserDetails userDetails = userDetailsService.loadUserByUsername(getUsernameFromJWT(token));
//        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
//    }
//}


import com.papaymoni.middleware.security.CustomUserDetailsService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationInMs;

    private final RedisTemplate<String, String> redisTemplate;
    private final CustomUserDetailsService userDetailsService;

    @PostConstruct
    protected void init() {
        jwtSecret = Base64.getEncoder().encodeToString(jwtSecret.getBytes());
    }

    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        String token = Jwts.builder()
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(SignatureAlgorithm.HS512, jwtSecret)
                .compact();

        // Store token in Redis with expiration
        String redisKey = "token:" + userDetails.getUsername();
        redisTemplate.opsForValue().set(redisKey, token);
        redisTemplate.expire(redisKey, jwtExpirationInMs, TimeUnit.MILLISECONDS);

        return token;
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public boolean validateToken(String authToken) {
        try {
            // First check if token is in Redis (faster)
            String username = getUsernameFromJWT(authToken);
            String redisKey = "token:" + username;
            String storedToken = redisTemplate.opsForValue().get(redisKey);

            // If token is not in Redis or doesn't match, it might be invalid or expired
            if (storedToken == null || !storedToken.equals(authToken)) {
                // Fall back to JWT validation
                Jwts.parser().setSigningKey(jwtSecret).parseClaimsJws(authToken);
                return true;
            }

            // Token found in Redis, it's valid
            return true;
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
            // Remove expired token from Redis
            try {
                String username = ex.getClaims().getSubject();
                redisTemplate.delete("token:" + username);
            } catch (Exception e) {
                log.error("Error cleaning up expired token", e);
            }
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }

    public void invalidateToken(String username) {
        redisTemplate.delete("token:" + username);
    }

    public Authentication getAuthentication(String token) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(getUsernameFromJWT(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
}