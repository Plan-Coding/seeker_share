package com.seeker.share.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "用户名不能为空") @Size(max = 50, message = "用户名不能超过 50 位") String username,
		@NotBlank(message = "登录凭据不能为空") String credential) { }
