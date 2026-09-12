package com.skillskeeper.skillskeeper.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

	private final TokenService tokenService;

	public AuthWebConfig(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AuthTokenInterceptor(tokenService))
				.addPathPatterns("/api/**")
				.excludePathPatterns("/api/auth/login");
	}
}
