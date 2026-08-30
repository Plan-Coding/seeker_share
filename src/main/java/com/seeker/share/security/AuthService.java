package com.seeker.share.security;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final int maximumFailedAttempts;

	public AuthService(
			AppUserRepository users,
			PasswordEncoder passwordEncoder,
			PasswordPolicy passwordPolicy,
			@Value("${seeker.security.max-failed-attempts}") int maximumFailedAttempts) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.maximumFailedAttempts = maximumFailedAttempts;
	}

	@Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
	public UserPrincipal login(String username, String password) {
		String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
		AppUser user = users.findByUsernameIgnoreCase(normalized)
				.orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
		if (!user.isEnabled()) throw new BadCredentialsException("账户已停用");
		if (!user.isAccountNonLocked()) throw new LockedException("登录失败次数过多，账户已锁定");
		if (!passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
			user.recordFailedLogin(maximumFailedAttempts);
			users.save(user);
			if (!user.isAccountNonLocked()) throw new LockedException("登录失败次数过多，账户已锁定");
			throw new BadCredentialsException("用户名或密码错误");
		}
		user.recordSuccessfulLogin();
		return UserPrincipal.from(users.save(user));
	}

	@Transactional
	public UserPrincipal changePassword(UserPrincipal principal, String currentPassword, String newPassword, String confirmation) {
		AppUser user = users.findById(principal.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账户不存在"));
		if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
		}
		if (newPassword == null || !newPassword.equals(confirmation)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "两次输入的新密码不一致");
		}
		if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
		}
		passwordPolicy.validate(newPassword, user.getUsername());
		user.changePassword(passwordEncoder.encode(newPassword));
		return UserPrincipal.from(users.save(user));
	}
}
