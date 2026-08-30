package com.seeker.share.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank @Size(max = 128) String currentPassword,
		@NotBlank @Size(max = 128) String newPassword,
		@NotBlank @Size(max = 128) String confirmation) { }
