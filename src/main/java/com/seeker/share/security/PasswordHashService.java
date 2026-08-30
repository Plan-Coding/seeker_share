package com.seeker.share.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 统一密码摘要与存储规则:
 * 1) 客户端与后端共享的加盐哈希:digest = hex(HMAC-SHA256(key=salt, message=password))
 * 2) 数据库保存 BCrypt(digest),而不是 BCrypt(明文)
 */
@Service
public class PasswordHashService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final PasswordEncoder passwordEncoder;

	public PasswordHashService(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	/** 生成 16 字节随机盐,返回 32 位小写 hex。 */
	public String newSalt() {
		byte[] salt = new byte[16];
		RANDOM.nextBytes(salt);
		return HexFormat.of().formatHex(salt);
	}

	/** 与前端完全一致的加盐哈希:hex(HMAC-SHA256(key=salt, message=password))。 */
	public String digest(String password, String salt) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] out = mac.doFinal(password.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(out);
		} catch (Exception exception) {
			throw new IllegalStateException("无法计算密码摘要", exception);
		}
	}

	/** 由明文密码计算 BCrypt 摘要,用于初始化等后端持有明文的场景。 */
	public String encode(String password, String salt) {
		return passwordEncoder.encode(digest(password, salt));
	}

	/** 由前端传来的加盐摘要直接计算 BCrypt,用于创建/重置/修改密码。 */
	public String encodeDigest(String digestHex) {
		return passwordEncoder.encode(digestHex);
	}

	/** 校验明文密码是否匹配(后端持有明文时使用)。 */
	public boolean matches(String password, String salt, String storedHash) {
		return passwordEncoder.matches(digest(password, salt), storedHash);
	}

	/** 校验前端传来的加盐摘要是否匹配。 */
	public boolean matchesDigest(String digestHex, String storedHash) {
		return passwordEncoder.matches(digestHex, storedHash);
	}
}
