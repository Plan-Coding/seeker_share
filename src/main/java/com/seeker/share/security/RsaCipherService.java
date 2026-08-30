package com.seeker.share.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.springframework.stereotype.Service;

/**
 * RSA 非对称加密:前端用公钥加密密码摘要,后端用私钥解密。
 * 使用 RSA-2048 + OAEP-SHA256。
 */
@Service
public class RsaCipherService {

	private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

	private final KeyPair keyPair;

	public RsaCipherService() {
		this.keyPair = generateKeyPair();
	}

	private KeyPair generateKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception exception) {
			throw new IllegalStateException("无法生成 RSA 密钥对", exception);
		}
	}

	/** 返回 SPKI(DER) 的 Base64 公钥,供前端 WebCrypto 导入。 */
	public String publicKeyBase64() {
		return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
	}

	/** 解密前端传来的 Base64 密文,返回 UTF-8 明文。 */
	public String decrypt(String base64Cipher) {
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			// 与 WebCrypto 保持一致:OAEP 摘要与 MGF1 摘要均为 SHA-256
			cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), oaepSha256());
			byte[] plain = cipher.doFinal(Base64.getDecoder().decode(base64Cipher));
			return new String(plain, StandardCharsets.UTF_8);
		} catch (Exception exception) {
			throw new IllegalArgumentException("无法解密凭据", exception);
		}
	}

	private OAEPParameterSpec oaepSha256() {
		return new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
	}
}
