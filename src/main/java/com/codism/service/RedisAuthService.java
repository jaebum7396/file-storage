package com.codism.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisAuthService {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis Key 패턴
    private static final String ACCESS_TOKEN_KEY = "auth:access_token:";
    private static final String REFRESH_TOKEN_KEY = "auth:refresh_token:";
    private static final String USER_SESSION_KEY = "auth:user_session:";
    private static final String LOGIN_ATTEMPT_KEY = "auth:login_attempt:";
    private static final String BLACKLIST_TOKEN_KEY = "auth:blacklist:";

    /**
     * Access Token을 Redis에 저장
     */
    public void storeAccessToken(String userCd, String accessToken, long expirationMs) {
        String key = ACCESS_TOKEN_KEY + userCd;
        try {
            redisTemplate.opsForValue().set(key, accessToken, Duration.ofMillis(expirationMs));
            log.debug("Access Token 저장 완료 - UserId: {}", userCd);
        } catch (Exception e) {
            log.error("Access Token 저장 실패 - UserId: {}, Error: {}", userCd, e.getMessage());
            throw new RuntimeException("토큰 저장에 실패했습니다", e);
        }
    }

    /**
     * Refresh Token을 Redis에 저장
     */
    public void storeRefreshToken(String userCd, String refreshToken, long expirationMs) {
        String key = REFRESH_TOKEN_KEY + userCd;
        try {
            redisTemplate.opsForValue().set(key, refreshToken, Duration.ofMillis(expirationMs));
            log.debug("Refresh Token 저장 완료 - UserId: {}", userCd);
        } catch (Exception e) {
            log.error("Refresh Token 저장 실패 - UserId: {}, Error: {}", userCd, e.getMessage());
            throw new RuntimeException("토큰 저장에 실패했습니다", e);
        }
    }

    /**
     * 사용자 세션 정보 저장
     */
    public void storeUserSession(String userCd, String sessionInfo, long expirationMs) {
        String key = USER_SESSION_KEY + userCd;
        try {
            redisTemplate.opsForValue().set(key, sessionInfo, Duration.ofMillis(expirationMs));
            log.debug("사용자 세션 저장 완료 - UserId: {}", userCd);
        } catch (Exception e) {
            log.error("사용자 세션 저장 실패 - UserId: {}, Error: {}", userCd, e.getMessage());
        }
    }

    /**
     * Access Token 조회
     */
    public String getAccessToken(String userCd) {
        String key = ACCESS_TOKEN_KEY + userCd;
        try {
            Object token = redisTemplate.opsForValue().get(key);
            return token != null ? token.toString() : null;
        } catch (Exception e) {
            log.error("Access Token 조회 실패 - UserId: {}, Error: {}", userCd, e.getMessage());
            return null;
        }
    }

    /**
     * Refresh Token 조회
     */
    public String getRefreshToken(String userCd) {
        String key = REFRESH_TOKEN_KEY + userCd;
        try {
            Object token = redisTemplate.opsForValue().get(key);
            return token != null ? token.toString() : null;
        } catch (Exception e) {
            log.error("Refresh Token 조회 실패 - UserId: {}, Error: {}", userCd, e.getMessage());
            return null;
        }
    }

    /**
     * 사용자 세션 정보 조회
     */
    public String getUserSession(String userCd) {
        String key = USER_SESSION_KEY + userCd;
        try {
            Object session = redisTemplate.opsForValue().get(key);
            return session != null ? session.toString() : null;
        } catch (Exception e) {
            log.error("사용자 세션 조회 실패 - UserId: {}, Error: {}", userCd, e.getMessage());
            return null;
        }
    }

    /**
     * 토큰이 Redis에 존재하는지 확인
     */
    public boolean isTokenExists(String userCd, String tokenType) {
        String key = tokenType.equals("ACCESS") ? ACCESS_TOKEN_KEY + userCd : REFRESH_TOKEN_KEY + userCd;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("토큰 존재 확인 실패 - UserId: {}, TokenType: {}, Error: {}", userCd, tokenType, e.getMessage());
            return false;
        }
    }

    /**
     * 사용자의 모든 토큰 삭제 (로그아웃)
     */
    public void deleteUserTokens(String userCd) {
        try {
            String accessTokenKey = ACCESS_TOKEN_KEY + userCd;
            String refreshTokenKey = REFRESH_TOKEN_KEY + userCd;
            String sessionKey = USER_SESSION_KEY + userCd;

            redisTemplate.delete(accessTokenKey);
            redisTemplate.delete(refreshTokenKey);
            redisTemplate.delete(sessionKey);

            log.info("사용자 토큰 삭제 완료 - UserId: {}", userCd);
        } catch (Exception e) {
            log.error("사용자 토큰 삭제 실패 - UserId: {}, Error: {}", userCd, e.getMessage());
            throw new RuntimeException("토큰 삭제에 실패했습니다", e);
        }
    }

    /**
     * 특정 토큰을 블랙리스트에 추가
     */
    public void addToBlacklist(String token, long expirationMs) {
        String key = BLACKLIST_TOKEN_KEY + token;
        try {
            redisTemplate.opsForValue().set(key, "BLACKLISTED", Duration.ofMillis(expirationMs));
            log.debug("토큰 블랙리스트 추가 완료");
        } catch (Exception e) {
            log.error("토큰 블랙리스트 추가 실패 - Error: {}", e.getMessage());
        }
    }

    /**
     * 토큰이 블랙리스트에 있는지 확인
     */
    public boolean isTokenBlacklisted(String token) {
        String key = BLACKLIST_TOKEN_KEY + token;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("토큰 블랙리스트 확인 실패 - Error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 로그인 시도 횟수 증가
     */
    public void incrementLoginAttempt(String email) {
        String key = LOGIN_ATTEMPT_KEY + email;
        try {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, 15, TimeUnit.MINUTES); // 15분 후 만료
        } catch (Exception e) {
            log.error("로그인 시도 횟수 증가 실패 - Email: {}, Error: {}", email, e.getMessage());
        }
    }

    /**
     * 로그인 시도 횟수 조회
     */
    public int getLoginAttemptCount(String email) {
        String key = LOGIN_ATTEMPT_KEY + email;
        try {
            Object count = redisTemplate.opsForValue().get(key);
            return count != null ? Integer.parseInt(count.toString()) : 0;
        } catch (Exception e) {
            log.error("로그인 시도 횟수 조회 실패 - Email: {}, Error: {}", email, e.getMessage());
            return 0;
        }
    }

    /**
     * 로그인 시도 횟수 초기화
     */
    public void resetLoginAttempt(String email) {
        String key = LOGIN_ATTEMPT_KEY + email;
        try {
            redisTemplate.delete(key);
            log.debug("로그인 시도 횟수 초기화 완료 - Email: {}", email);
        } catch (Exception e) {
            log.error("로그인 시도 횟수 초기화 실패 - Email: {}, Error: {}", email, e.getMessage());
        }
    }

    /**
     * 로그인 시도가 제한되었는지 확인
     */
    public boolean isLoginAttemptLimited(String email, int maxAttempts) {
        return getLoginAttemptCount(email) >= maxAttempts;
    }

    /**
     * Refresh Token으로 새로운 토큰 세트 갱신
     */
    public void refreshTokens(String userCd, String newAccessToken, String newRefreshToken,
                              long accessTokenExpiration, long refreshTokenExpiration) {
        try {
            // 기존 토큰 삭제
            deleteUserTokens(userCd);

            // 새로운 토큰 저장
            storeAccessToken(userCd, newAccessToken, accessTokenExpiration);
            storeRefreshToken(userCd, newRefreshToken, refreshTokenExpiration);

            log.info("토큰 갱신 완료 - UserId: {}", userCd);
        } catch (Exception e) {
            log.error("토큰 갱신 실패 - UserId: {}, Error: {}", userCd, e.getMessage());
            throw new RuntimeException("토큰 갱신에 실패했습니다", e);
        }
    }
}