package com.codism.exception;

import org.springframework.http.HttpStatus;

/**
 * 성인 이미지가 감지되었을 때 발생하는 예외
 */
public class AdultContentException extends CustomException {

    private static final String CODE = "ADULT_CONTENT";

    public AdultContentException(String message) {
        super(message, HttpStatus.BAD_REQUEST, CODE);
    }

    public AdultContentException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_REQUEST, CODE);
        initCause(cause);
    }

    /**
     * 기본 메시지로 예외 생성
     *
     * @return AdultContentException 인스턴스
     */
    public static AdultContentException create() {
        return new AdultContentException("성인 이미지는 업로드할 수 없습니다.");
    }

    /**
     * 사용자 지정 메시지로 예외 생성
     *
     * @param message 사용자 지정 메시지
     * @return AdultContentException 인스턴스
     */
    public static AdultContentException create(String message) {
        return new AdultContentException(message);
    }
}