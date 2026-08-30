package com.seeker.share.security;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

/** 测试用:复现前端“加盐哈希 + RSA-OAEP 加密”的凭据构造逻辑。 */
public final class CredentialTestHelper {

	private CredentialTestHelper() { }

	public static String digest(String password, String salt) throws Exception {
		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(salt.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		byte[] out = mac.doFinal(password.getBytes(StandardCharsets.UTF_8));
		return HexFormat.of().formatHex(out);
	}

	public static String encrypt(String publicKeyBase64, String plaintext) throws Exception {
		byte[] der = Base64.getDecoder().decode(publicKeyBase64);
		PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
		Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
		OAEPParameterSpec oaep = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
		cipher.init(Cipher.ENCRYPT_MODE, key, oaep);
		byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(encrypted);
	}

	public static String buildCredential(String publicKeyBase64, String salt, String nonce, String password)
			throws Exception {
		return encrypt(publicKeyBase64, digest(password, salt) + ":" + nonce);
	}
}
