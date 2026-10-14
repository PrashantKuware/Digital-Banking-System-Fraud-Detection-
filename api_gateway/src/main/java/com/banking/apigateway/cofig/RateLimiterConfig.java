package com.banking.apigateway.cofig;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {
	
	@Bean
	public KeyResolver keyResolver() {
		return ServerWebExchange exchange -> Mono.just(
				exchange.getRequest()
				.getRemoteAddress()
				.getAddress()
				.getHostAddress()
				);
	}
}
