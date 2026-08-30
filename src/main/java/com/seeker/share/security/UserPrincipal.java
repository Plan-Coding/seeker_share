package com.seeker.share.security;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public record UserPrincipal(
		UUID id,
		String username,
		String password,
		boolean enabled,
		boolean accountNonLocked,
		boolean passwordChangeRequired,
		Set<String> roles,
		Set<String> permissions) implements UserDetails {

	public static UserPrincipal from(AppUser user) {
		Set<String> roles = user.getRoles().stream().map(RoleEntity::getName).collect(Collectors.toSet());
		Set<String> permissions = user.getRoles().stream().flatMap(role -> role.getPermissions().stream())
				.map(permission -> permission.getCode().name()).collect(Collectors.toSet());
		return new UserPrincipal(user.getId(), user.getUsername(), user.getPasswordHash(), user.isEnabled(),
				user.isAccountNonLocked(), user.isPasswordChangeRequired(), roles, permissions);
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		if (passwordChangeRequired) return List.of(new SimpleGrantedAuthority("PASSWORD_CHANGE_REQUIRED"));
		return java.util.stream.Stream.concat(
				roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)),
				permissions.stream().map(SimpleGrantedAuthority::new)).toList();
	}

	@Override public String getUsername() { return username; }
	@Override public String getPassword() { return password; }
	@Override public boolean isAccountNonExpired() { return true; }
	@Override public boolean isCredentialsNonExpired() { return true; }
	@Override public boolean isAccountNonLocked() { return accountNonLocked; }
	@Override public boolean isEnabled() { return enabled; }
}
