package com.codism.config.error;

import com.codism.config.error.entity.ApiException;
import com.codism.config.error.entity.ErrorEntity;
import com.codism.config.error.entity.ErrorEntityBody;
import com.codism.config.error.type.ErrorCode;
import com.codism.exception.CustomException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SecurityException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리 클래스
 * 모든 예외는 여기서 처리됨
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorEntityBody> handleCustomException(CustomException e) {
        log.error("CustomException - Code: {}, Status: {}, Message: {}",
                e.getCode(), e.getStatus(), e.getMessage());
        ErrorCode errorCode = mapHttpStatusToErrorCode(e.getStatus());
        return ErrorEntity.status(errorCode).body(e.getMessage());
    }

    private ErrorCode mapHttpStatusToErrorCode(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> ErrorCode.BAD_REQUEST;
            case UNAUTHORIZED -> ErrorCode.UNAUTHORIZED;  // 401 처리 추가
            case FORBIDDEN -> ErrorCode.FORBIDDEN;        // 403 처리 추가
            case NOT_FOUND -> ErrorCode.NOT_FOUND;
            case CONFLICT -> ErrorCode.DUPLICATE_KEY;
            case REQUEST_TIMEOUT -> ErrorCode.TIME_OUT;
            default -> ErrorCode.INTERNAL_SERVER_ERROR;
        };
    }

    /* JWT 관련 예외 처리 - RuntimeException보다 먼저 처리되도록 위치 */
    @ExceptionHandler(ExpiredJwtException.class)
    protected ResponseEntity<ErrorEntityBody> handleExpiredJwtException(ExpiredJwtException e) {
        log.warn("JWT 토큰 만료: {}", e.getMessage());
        return ErrorEntity.status(ErrorCode.UNAUTHORIZED).body("토큰이 만료되었습니다. 다시 로그인해주세요.");
    }

    @ExceptionHandler(UnsupportedJwtException.class)
    protected ResponseEntity<ErrorEntityBody> handleUnsupportedJwtException(UnsupportedJwtException e) {
        log.warn("지원되지 않는 JWT 토큰: {}", e.getMessage());
        return ErrorEntity.status(ErrorCode.UNAUTHORIZED).body("지원되지 않는 토큰 형식입니다.");
    }

    @ExceptionHandler(MalformedJwtException.class)
    protected ResponseEntity<ErrorEntityBody> handleMalformedJwtException(MalformedJwtException e) {
        log.warn("잘못된 형식의 JWT 토큰: {}", e.getMessage());
        return ErrorEntity.status(ErrorCode.UNAUTHORIZED).body("잘못된 형식의 토큰입니다.");
    }

    @ExceptionHandler(SecurityException.class)
    protected ResponseEntity<ErrorEntityBody> handleSecurityException(SecurityException e) {
        log.warn("JWT 토큰 서명 검증 실패: {}", e.getMessage());
        return ErrorEntity.status(ErrorCode.UNAUTHORIZED).body("토큰 서명이 유효하지 않습니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    protected ResponseEntity<ErrorEntityBody> handleIllegalArgumentException(IllegalArgumentException e) {
        // JWT 관련 IllegalArgumentException인지 확인
        if (e.getMessage() != null && e.getMessage().contains("JWT")) {
            log.warn("JWT 토큰 관련 오류: {}", e.getMessage());
            return ErrorEntity.status(ErrorCode.UNAUTHORIZED).body("토큰이 비어있습니다.");
        }
        log.error("IllegalArgumentException", e);
        return ErrorEntity.status(ErrorCode.BAD_REQUEST).body(e.getMessage());
    }

    /* @Valid 또는 @Validated 바인딩 에러 처리 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorEntityBody> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        log.error("handleMethodArgumentNotValidException", e);
        BindingResult bindingResult = e.getBindingResult();
        FieldError fieldError = bindingResult.getFieldErrors().get(0);
        return ErrorEntity.status(ErrorCode.BAD_VALID).body(fieldError.getDefaultMessage());
    }

    /* @RequestBody 바인딩 에러 처리 */
    @ExceptionHandler(BindException.class)
    protected ResponseEntity<ErrorEntityBody> handleBindException(BindException e) {
        log.error("handleBindException", e);
        BindingResult bindingResult = e.getBindingResult();
        FieldError fieldError = bindingResult.getFieldErrors().get(0);
        return ErrorEntity.status(ErrorCode.BAD_VALID).body(fieldError.getField() + fieldError.getDefaultMessage());
    }

    /* API 에러 응답 처리 */
    @ExceptionHandler(ApiException.class)
    protected ResponseEntity<ErrorEntityBody> handleApiException(ApiException e) {
        log.error("ApiException - {}", e.getMessage());
        return ErrorEntity.status(e.getErrorCode()).body(e.getBody());
    }

    /* 런타임 예외 처리 */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorEntityBody> handleRuntimeException(RuntimeException e) {
        if (e instanceof ApiException apiException) {
            return this.handleApiException(apiException);
        }
        if (e.getCause() instanceof ApiException apiException) {
            return this.handleApiException(apiException);
        }

        // JWT 관련 예외가 RuntimeException으로 래핑된 경우 처리
        Throwable cause = e.getCause();
        if (cause instanceof ExpiredJwtException) {
            return handleExpiredJwtException((ExpiredJwtException) cause);
        } else if (cause instanceof UnsupportedJwtException) {
            return handleUnsupportedJwtException((UnsupportedJwtException) cause);
        } else if (cause instanceof MalformedJwtException) {
            return handleMalformedJwtException((MalformedJwtException) cause);
        } else if (cause instanceof SecurityException) {
            return handleSecurityException((SecurityException) cause);
        }

        log.error("handleRuntimeException", e);
        return ErrorEntity.status(ErrorCode.INTERNAL_SERVER_ERROR).body();
    }

    /* Json parse 예외 처리 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEntityBody> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        log.error("handleHttpMessageNotReadableException", e);

        // enum 값 오류인지 확인
        if (e.getCause() instanceof InvalidFormatException invalidFormatException) {
            if (invalidFormatException.getTargetType() != null && invalidFormatException.getTargetType().isEnum()) {
                return ErrorEntity.status(ErrorCode.BAD_REQUEST).body("허용되는 enum 값이 아닙니다.");
            }
        }

        return ErrorEntity.status(ErrorCode.JSON_PARSE_ERROR).body(null);
    }

    /* 전역 예외 처리 */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorEntityBody> handleGlobalException(Exception e) {
        log.error("handleGlobalException", e);
        return ErrorEntity.status(ErrorCode.INTERNAL_SERVER_ERROR).body();
    }
}