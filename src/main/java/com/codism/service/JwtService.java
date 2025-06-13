package com.codism.service;

import com.codism.exception.CustomException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtService(
            @Value("${jwt.secret:defaultSecretKeyForDevelopmentOnlyNotForProduction}") String secret,
            @Value("${jwt.access-token-expiration:3600000}") long accessTokenExpiration, // 1시간
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration // 7일
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String getAuthorization(HttpServletRequest httpRequest) throws CustomException {
        String authorizationHeader = httpRequest.getHeader("Authorization");
        if (authorizationHeader == null) {
            throw CustomException.unauthorized("Authorization 헤더가 없습니다.");
        }
        if (!authorizationHeader.startsWith("Bearer ")) {
            throw CustomException.unauthorized("Bearer 토큰 형식이 올바르지 않습니다.");
        }
        return authorizationHeader.substring(7); // "Bearer " 이후의 토큰 반환
    }

    public String getUserCd(HttpServletRequest httpRequest) throws CustomException {
        String token = getAuthorization(httpRequest);

        if (token == null || token.trim().isEmpty()) {
            throw CustomException.unauthorized("토큰이 비어있습니다.");
        }

        try {
            Claims claims = getClaimsFromToken(token);
            String userCd = claims.getSubject();

            if (userCd == null || userCd.trim().isEmpty()) {
                throw CustomException.unauthorized("토큰에서 사용자 정보를 찾을 수 없습니다.");
            }

            log.debug("토큰에서 사용자 코드 추출 성공: {}", userCd);
            return userCd;

        } catch (ExpiredJwtException e) {
            log.warn("만료된 토큰으로 접근 시도: {}", e.getMessage());
            throw CustomException.unauthorized("토큰이 만료되었습니다. 다시 로그인해주세요.");
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 토큰 형식: {}", e.getMessage());
            throw CustomException.unauthorized("지원되지 않는 토큰 형식입니다.");
        } catch (MalformedJwtException e) {
            log.warn("잘못된 형식의 토큰: {}", e.getMessage());
            throw CustomException.unauthorized("잘못된 형식의 토큰입니다.");
        } catch (SecurityException e) {
            log.warn("토큰 서명 검증 실패: {}", e.getMessage());
            throw CustomException.unauthorized("토큰 서명이 유효하지 않습니다.");
        } catch (IllegalArgumentException e) {
            log.warn("토큰이 비어있음: {}", e.getMessage());
            throw CustomException.unauthorized("토큰이 비어있습니다.");
        } catch (Exception e) {
            log.error("토큰 처리 중 예상치 못한 오류 발생: {}", e.getMessage(), e);
            throw CustomException.unauthorized("토큰 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * Access Token 생성
     */
    public String generateAccessToken(String userCd, String email, Boolean isPremium) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userCd", userCd);
        claims.put("email", email);
        claims.put("isPremium", isPremium);
        claims.put("tokenType", "ACCESS");

        return createToken(claims, userCd, accessTokenExpiration);
    }

    /**
     * Refresh Token 생성
     */
    public String generateRefreshToken(String userCd) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userCd", userCd);
        claims.put("tokenType", "REFRESH");

        return createToken(claims, userCd.toString(), refreshTokenExpiration);
    }

    /**
     * 토큰 생성
     */
    private String createToken(Map<String, Object> claims, String subject, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    public String getUserCdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * 토큰에서 이메일 추출
     */
    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("email", String.class);
    }

    /**
     * 토큰에서 프리미엄 여부 추출
     */
    public Boolean getIsPremiumFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("isPremium", Boolean.class);
    }

    /**
     * 토큰 타입 확인
     */
    public String getTokenType(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.get("tokenType", String.class);
    }

    /**
     * 토큰 만료 시간 확인
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getExpiration();
    }

    /**
     * 토큰에서 Claims 추출
     */
    private Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("JWT 토큰이 만료되었습니다: {}", e.getMessage());
            throw new RuntimeException("토큰이 만료되었습니다", e);
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰입니다: {}", e.getMessage());
            throw new RuntimeException("지원되지 않는 토큰입니다", e);
        } catch (MalformedJwtException e) {
            log.warn("잘못된 형식의 JWT 토큰입니다: {}", e.getMessage());
            throw new RuntimeException("잘못된 형식의 토큰입니다", e);
        } catch (SecurityException e) {
            log.warn("JWT 토큰 서명이 유효하지 않습니다: {}", e.getMessage());
            throw new RuntimeException("토큰 서명이 유효하지 않습니다", e);
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어있습니다: {}", e.getMessage());
            throw new RuntimeException("토큰이 비어있습니다", e);
        }
    }

    /**
     * 토큰 유효성 검사
     */
    public boolean isTokenValid(String token) {
        try {
            getClaimsFromToken(token);
            return true;
        } catch (Exception e) {
            log.debug("토큰 유효성 검사 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 토큰 만료 확인
     */
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Access Token 여부 확인
     */
    public boolean isAccessToken(String token) {
        try {
            return "ACCESS".equals(getTokenType(token));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Refresh Token 여부 확인
     */
    public boolean isRefreshToken(String token) {
        try {
            return "REFRESH".equals(getTokenType(token));
        } catch (Exception e) {
            return false;
        }
    }
}