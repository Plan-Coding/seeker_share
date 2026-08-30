package com.seeker.share.document;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * WebSocket 握手鉴权：从 HTTP 会话解析已登录用户,写入 session attributes,
 * 供 {@link DocumentWebSocketHandler} 校验文档权限使用。
 */
@Component
public class DocumentAuthHandshakeInterceptor implements HandshakeInterceptor {

	private final SecurityContextRepository contextRepository;

	public DocumentAuthHandshakeInterceptor(SecurityContextRepository contextRepository) {
		this.contextRepository = contextRepository;
	}

	@Override
	public boolean beforeHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		if (request instanceof ServletServerHttpRequest servletRequest) {
			SecurityContext context = contextRepository
					.loadDeferredContext(servletRequest.getServletRequest())
					.get();
			Authentication authentication = context.getAuthentication();
			if (authentication != null && authentication.isAuthenticated()
					&& !(authentication.getPrincipal() instanceof String)) {
				attributes.put("authentication", authentication);
				return true;
			}
		}
		response.setStatusCode(HttpStatus.UNAUTHORIZED);
		return false;
	}

	@Override
	public void afterHandshake(
			ServerHttpRequest request,
			ServerHttpResponse response,
			WebSocketHandler wsHandler,
			Exception exception) { }
}
