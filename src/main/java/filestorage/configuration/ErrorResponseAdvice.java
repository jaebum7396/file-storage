package filestorage.configuration;

import filestorage.model.Response;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ErrorResponseAdvice {
	private Logger logger = LoggerFactory.getLogger(ErrorResponseAdvice.class);
	
	@ExceptionHandler(Exception.class)
	public ResponseEntity handleException(Exception e) {
		Response responseResult;
        Map<String, Object> resultMap = new LinkedHashMap<String, Object>();
		e.printStackTrace();
        responseResult = Response.builder()
                .message("서버쪽 오류가 발생했습니다. 관리자에게 문의하십시오")
                .result(resultMap).build();
        return ResponseEntity.internalServerError().body(responseResult);
	}
	
	@ExceptionHandler(BadCredentialsException.class)
	public ResponseEntity handleBadCredentialsException(BadCredentialsException e) {
		Response responseResult;
        Map<String, Object> resultMap = new LinkedHashMap<String, Object>();
		e.printStackTrace();
        responseResult = Response.builder()
                .message("인증되지 않은 사용자입니다.")
                .result(resultMap).build();
        return ResponseEntity.internalServerError().body(responseResult);
	}

	@ExceptionHandler(ExpiredJwtException.class)
	public ResponseEntity handleExpiredJwtException(ExpiredJwtException e) {
		System.out.println("ExpiredJwtException");
		Response responseResult;
		Map<String, Object> resultMap = new LinkedHashMap<String, Object>();
		e.printStackTrace();
		responseResult = Response.builder()
				.message("로그인 시간이 만료되었습니다.")
				.result(resultMap).build();
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseResult);
	}
}
