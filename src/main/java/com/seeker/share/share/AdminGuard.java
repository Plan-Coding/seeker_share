package com.seeker.share.share;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminGuard {

	private final String token;

	public AdminGuard(@Value("${seeker.share.admin-token:}") String token) {
		this.token = token;
	}

	public boolean isProtected() {
		return !token.isBlank();
	}

	public void verify(String suppliedToken) {
		if (!isProtected()) {
			return;
		}
		byte[] expected = token.getBytes(StandardCharsets.UTF_8);
		byte[] supplied = (suppliedToken == null ? "" : suppliedToken).getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(expected, supplied)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "管理员口令不正确");
		}
	}
}
