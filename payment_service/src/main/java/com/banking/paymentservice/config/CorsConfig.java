package com.banking.paymentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

@Configuration
public class CorsConfig {

	@Bean
	public WebMvcConfigrer corsConfigrer()
	{
		return new WebMvcConfigrer() {
			public void addCorsMappings(CorsRegistry registry) {
				registry.addMapping("/api/**")
				.allowedHeaders("*")
				.allowedMethods("GET", "POST", "PUT", "DELETE")
				.allowedHeaders("*");
			}
		}
	}
}
