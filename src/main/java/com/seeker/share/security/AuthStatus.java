package com.seeker.share.security;

import java.util.Set;

public record AuthStatus(
		boolean authenticated,
		String username,
		Set<String> roles,
		Set<String> permissions,
		boolean passwordChangeRequired,
		String csrfToken) {

	public static AuthStatus anonymous(String csrfToken) {
		return new AuthStatus(false, null, Set.of(), Set.of(), false, csrfToken);
	}

	public static AuthStatus authenticated(UserPrincipal principal, String csrfToken) {
		return new AuthStatus(true, principal.username(), principal.roles(), principal.permissions(),
				principal.passwordChangeRequired(), csrfToken);
	}
}
