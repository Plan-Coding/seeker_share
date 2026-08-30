package com.seeker.share.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 一次性 nonce 存储,防止加密凭据被重放。
 * 每个 nonce 绑定一个盐:新用户创建时,后端据此取得前端使用的盐。
 */
@Component
public class PreloginNonceStore {

	private static final Duration TTL = Duration.ofMinutes(5);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	public String issue(String salt) {
		byte[] nonceBytes = new byte[16];
		RANDOM.nextBytes(nonceBytes);
		String nonce = HexFormat.of().formatHex(nonceBytes);
		entries.put(nonce, new Entry(salt, Instant.now().plus(TTL)));
		purgeExpired();
		return nonce;
	}

	/** 取出并作废 nonce;不存在或过期返回 null。 */
	public String consume(String nonce) {
		if (nonce == null) return null;
		Entry entry = entries.remove(nonce);
		if (entry == null || entry.expiresAt().isBefore(Instant.now())) return null;
		return entry.salt();
	}

	private void purgeExpired() {
		Instant now = Instant.now();
		entries.entrySet().removeIf(item -> item.getValue().expiresAt().isBefore(now));
	}

	private record Entry(String salt, Instant expiresAt) { }
}
