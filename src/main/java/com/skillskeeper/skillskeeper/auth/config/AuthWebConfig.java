package com.skillskeeper.skillskeeper.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.skillskeeper.skillskeeper.auth.service.TokenAuthority;
import com.skillskeeper.skillskeeper.auth.web.AuthTokenInterceptor;

@Configuration
public class AuthWebConfig implements WebMvcConfigurer {

	private final TokenAuthority tokenAuthority;

	public AuthWebConfig(TokenAuthority tokenAuthority) {
		this.tokenAuthority = tokenAuthority;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(new AuthTokenInterceptor(tokenAuthority))
				.addPathPatterns("/api/**")
				.excludePathPatterns("/api/auth/login");
	}
}
