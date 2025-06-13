package com.codism.security;

import com.codism.service.JwtService;
import com.codism.service.RedisAuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final RedisAuthService redisAuthService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractTokenFromRequest(request);

            if (token != null) {
                // 토큰이 있을 때만 인증 시도
                authenticateUser(request, token);
            }
        } catch (Exception e) {
            log.warn("JWT 인증 처리 중 오류 발생 - URI: {}, Method: {}, Error: {}",
                    request.getRequestURI(), request.getMethod(), e.getMessage());
            // 인증 실패 시 SecurityContext 초기화
            SecurityContextHolder.clearContext();
        }

        // 예외가 발생해도 항상 다음 필터로 진행 (중요!)
        filterChain.doFilter(request, response);
    }

    /**
     * 요청에서 JWT 토큰 추출
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        try {
            String bearerToken = request.getHeader("Authorization");
            if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
                String token = bearerToken.substring(7);
                return token.trim().isEmpty() ? null : token;
            }
        } catch (Exception e) {
            log.debug("토큰 추출 중 오류: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 사용자 인증 처리
     */
    private void authenticateUser(HttpServletRequest request, String token) {
        try {
            // 1. 토큰 유효성 검사
            if (!jwtService.isTokenValid(token)) {
                log.debug("유효하지 않은 토큰");
                return;
            }

            // 2. 토큰 타입 확인 (Access Token만 허용)
            if (!jwtService.isAccessToken(token)) {
                log.debug("Access Token이 아닌 토큰으로 인증 시도");
                return;
            }

            // 3. 토큰에서 사용자 정보 추출
            String userCd = null;
            String email = null;
            Boolean isPremium = null;

            try {
                userCd = jwtService.getUserCdFromToken(token);
                email = jwtService.getEmailFromToken(token);
                isPremium = jwtService.getIsPremiumFromToken(token);
            } catch (Exception e) {
                log.debug("토큰에서 사용자 정보 추출 실패: {}", e.getMessage());
                return;
            }

            // 4. 필수 정보 검증
            if (userCd == null || userCd.trim().isEmpty()) {
                log.debug("토큰에서 유효한 사용자 ID를 추출할 수 없음");
                return;
            }

            // 5. Redis 관련 검증 (Redis 오류는 무시하고 진행)
            try {
                // 블랙리스트 확인
                if (redisAuthService.isTokenBlacklisted(token)) {
                    log.debug("블랙리스트에 등록된 토큰 - UserCd: {}", userCd);
                    return;
                }

                // Redis에 저장된 토큰과 비교
                String storedToken = redisAuthService.getAccessToken(userCd);
                if (storedToken != null && !token.equals(storedToken)) {
                    log.debug("Redis에 저장된 토큰과 불일치 - UserCd: {}", userCd);
                    return;
                }
            } catch (Exception redisException) {
                // Redis 연결 오류 등은 로그만 남기고 인증은 계속 진행
                log.warn("Redis 검증 중 오류 발생, 인증 진행 - UserCd: {}, Error: {}",
                        userCd, redisException.getMessage());
            }

            // 6. 권한 설정 (null 체크 추가)
            List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                    new SimpleGrantedAuthority(Boolean.TRUE.equals(isPremium) ? "ROLE_PREMIUM_USER" : "ROLE_USER")
            );

            // 7. 인증 객체 생성
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userCd, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            // 8. SecurityContext에 인증 정보 설정
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("JWT 인증 성공 - UserCd: {}, Email: {}, IsPremium: {}", userCd, email, isPremium);

        } catch (Exception e) {
            log.warn("사용자 인증 처리 중 오류 발생: {}", e.getMessage());
            SecurityContextHolder.clearContext();
            // 예외를 다시 던지지 않음 - 요청이 계속 진행되도록
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // 모든 요청에 대해 필터를 실행 (토큰이 없어도 상관없음)
        // 시큐리티에서 모든 요청을 permitAll()로 설정했으므로 여기서는 모든 요청을 처리
        return false;
    }
}