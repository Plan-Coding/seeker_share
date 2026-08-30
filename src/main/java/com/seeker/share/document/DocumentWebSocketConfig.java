package com.seeker.share.document;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class DocumentWebSocketConfig implements WebSocketConfigurer {

	private final DocumentWebSocketHandler handler;
	private final DocumentAuthHandshakeInterceptor authInterceptor;

	public DocumentWebSocketConfig(DocumentWebSocketHandler handler, DocumentAuthHandshakeInterceptor authInterceptor) {
		this.handler = handler;
		this.authInterceptor = authInterceptor;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/ws/documents/{docId}")
				.addInterceptors(authInterceptor)
				.setAllowedOriginPatterns("*");
	}
}
