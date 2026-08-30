package com.seeker.share.document;

import com.seeker.share.security.UserPrincipal;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 文档协作中继：以「文档房间」为单位。
 * 二进制帧 = Yjs 增量更新(对服务端透明):追加到持久化日志并广播给同房间其他客户端;
 * 文本帧 = 控制消息(保存 / 生成版本)。新客户端加入时先重放持久化状态与增量。
 */
@Component
public class DocumentWebSocketHandler implements WebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(DocumentWebSocketHandler.class);
	private static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "unauthorized");
	private static final CloseStatus NOT_FOUND = new CloseStatus(4404, "document not found");

	private final DocumentService documents;
	private final Map<UUID, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();
	private final Map<WebSocketSession, Object> sendLocks = new ConcurrentHashMap<>();

	public DocumentWebSocketHandler(DocumentService documents) {
		this.documents = documents;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) throws Exception {
		Authentication authentication = (Authentication) session.getAttributes().get("authentication");
		if (authentication == null
				|| authentication.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("DOCUMENT_READ"))) {
			session.close(UNAUTHORIZED);
			return;
		}
		UUID docId = documentId(session);
		if (docId == null || !documents.exists(docId)) {
			session.close(NOT_FOUND);
			return;
		}
		rooms.computeIfAbsent(docId, key -> ConcurrentHashMap.newKeySet()).add(session);

		List<byte[]> replay = documents.replayUpdates(docId);
		if (replay.isEmpty()) {
			byte[] state = documents.currentState(docId);
			if (state != null) replay = List.of(state);
		}
		for (byte[] update : replay) {
			safeSend(session, new BinaryMessage(update));
		}
		broadcastMembers(docId);
	}

	@Override
	public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
		UUID docId = documentId(session);
		if (docId == null) return;
		Set<WebSocketSession> room = rooms.get(docId);
		if (message instanceof BinaryMessage binary) {
			byte[] payload = copy(binary.getPayload());
			documents.appendUpdate(docId, payload);
			relay(room, session, new BinaryMessage(payload));
		} else if (message instanceof TextMessage text) {
			handleControl(session, text.getPayload(), docId);
		}
	}

	private void handleControl(WebSocketSession session, String payload, UUID docId) throws Exception {
		String username = username(session);
		if (payload.startsWith("{\"type\":\"save\"")) {
			String content = jsonField(payload, "content");
			String state = jsonField(payload, "state");
			documents.saveCollaborative(docId, content, decode(state), username);
			TextMessage saved = new TextMessage("{\"type\":\"saved\",\"by\":\"" + escape(username) + "\"}");
			relay(rooms.get(docId), null, saved);
		} else if (payload.startsWith("{\"type\":\"version\"")) {
			DocumentVersionView view = documents.createVersion(docId, username);
			safeSend(session, new TextMessage("{\"type\":\"versioned\",\"versionNo\":" + view.versionNo() + "}"));
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
		UUID docId = documentId(session);
		if (docId == null) return;
		Set<WebSocketSession> room = rooms.get(docId);
		if (room != null) {
			room.remove(session);
			sendLocks.remove(session);
			if (room.isEmpty()) rooms.remove(docId);
			else broadcastMembers(docId);
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		log.warn("文档 WS 传输错误 {}: {}", session.getId(), exception.getMessage());
		session.close(CloseStatus.SERVER_ERROR);
	}

	@Override
	public boolean supportsPartialMessages() {
		return false;
	}

	/* ---------- 工具方法 ---------- */

	private void relay(Set<WebSocketSession> room, WebSocketSession except, WebSocketMessage<?> message) {
		if (room == null) return;
		for (WebSocketSession peer : room) {
			if (peer == except || !peer.isOpen()) continue;
			safeSend(peer, message);
		}
	}

	private void broadcastMembers(UUID docId) {
		Set<WebSocketSession> room = rooms.get(docId);
		if (room == null) return;
		TextMessage message = new TextMessage("{\"type\":\"members\",\"count\":" + room.size() + "}");
		relay(room, null, message);
	}

	/** 对同一会话的发送做串行化,避免并发写导致 PARTIAL_WRITING 错误。 */
	private void safeSend(WebSocketSession session, WebSocketMessage<?> message) {
		Object lock = sendLocks.computeIfAbsent(session, key -> new Object());
		synchronized (lock) {
			try {
				if (session.isOpen()) session.sendMessage(message);
			} catch (Exception error) {
				log.warn("发送失败 {}: {}", session.getId(), error.getMessage());
			}
		}
	}

	private static UUID documentId(WebSocketSession session) {
		String path = session.getUri() == null ? "" : session.getUri().getPath();
		int idx = path.lastIndexOf('/');
		if (idx < 0) return null;
		try {
			return UUID.fromString(path.substring(idx + 1));
		} catch (IllegalArgumentException error) {
			return null;
		}
	}

	private static String username(WebSocketSession session) {
		Authentication authentication = (Authentication) session.getAttributes().get("authentication");
		if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
			return principal.getUsername();
		}
		return "unknown";
	}

	private static byte[] copy(ByteBuffer buffer) {
		byte[] bytes = new byte[buffer.remaining()];
		buffer.get(bytes);
		return bytes;
	}

	private static byte[] decode(String state) {
		if (state == null || state.isBlank()) return null;
		return java.util.Base64.getDecoder().decode(state);
	}

	private static String jsonField(String json, String key) {
		String prefix = "\"" + key + "\":\"";
		int start = json.indexOf(prefix);
		if (start < 0) return null;
		int valueStart = start + prefix.length();
		StringBuilder value = new StringBuilder();
		for (int i = valueStart; i < json.length(); i++) {
			char ch = json.charAt(i);
			if (ch == '\\') { i++; value.append(json.charAt(i)); }
			else if (ch == '"') break;
			else value.append(ch);
		}
		return value.toString();
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
