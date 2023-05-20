package filestorage.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
	@Value("${cloud.gateway.uri}")
	String GATEWAY_URI;
	@Value("${spring.profiles.active}")
	String ACTIVE_PROFILE;
	@Override
	public void addInterceptors(InterceptorRegistry reg) {
		reg.addInterceptor(new MyInterceptor(GATEWAY_URI, ACTIVE_PROFILE))
			.addPathPatterns("/*")
			.excludePathPatterns("/css/**", "/images/**", "/js/**");
	}
	@Override
	public void addCorsMappings(CorsRegistry registry) {
		if(ACTIVE_PROFILE.equals("local")) {
			registry.addMapping("/**") // 모든 경로에 대해 CORS 설정 적용
				.allowedOrigins(GATEWAY_URI) // 허용할 도메인을 allowedOrigins에 지정
				.allowedMethods("*") // 허용할 HTTP 메서드 설정
				.allowedHeaders("*") // 허용할 헤더 설정
			;
		}
	}
}
