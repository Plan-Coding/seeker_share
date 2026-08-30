package com.seeker.share.security;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
		@NotBlank(message = "当前密码不能为空") String currentCredential,
		@NotBlank(message = "新密码不能为空") String newCredential) { }
