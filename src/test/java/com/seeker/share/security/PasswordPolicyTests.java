package com.seeker.share.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PasswordPolicyTests {

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void acceptsStrongPassword() {
		assertThatCode(() -> policy.validate("Violet-River#42", "admin")).doesNotThrowAnyException();
	}

	@Test
	void rejectsWeakOrUsernameBasedPasswords() {
		assertThatThrownBy(() -> policy.validate("admin-Password1!", "admin"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("不能包含用户名");
		assertThatThrownBy(() -> policy.validate("short", "admin"))
				.isInstanceOf(ResponseStatusException.class)
				.hasMessageContaining("密码强度不足");
	}
}
