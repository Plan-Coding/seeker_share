package com.seeker.share.security;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

	private final AppUserRepository users;
	private final PasswordHashService passwordHashService;
	private final int maximumFailedAttempts;

	public AuthService(
			AppUserRepository users,
			PasswordHashService passwordHashService,
			@Value("${seeker.security.max-failed-attempts}") int maximumFailedAttempts) {
		this.users = users;
		this.passwordHashService = passwordHashService;
		this.maximumFailedAttempts = maximumFailedAttempts;
	}

	@Transactional(noRollbackFor = {BadCredentialsException.class, LockedException.class})
	public UserPrincipal loginWithDigest(String username, String digestHex) {
		String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
		AppUser user = users.findByUsernameIgnoreCase(normalized)
				.orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));
		if (!user.isEnabled()) throw new BadCredentialsException("账户已停用");
		if (!user.isAccountNonLocked()) throw new LockedException("登录失败次数过多，账户已锁定");
		if (!passwordHashService.matchesDigest(digestHex == null ? "" : digestHex, user.getPasswordHash())) {
			user.recordFailedLogin(maximumFailedAttempts);
			users.save(user);
			if (!user.isAccountNonLocked()) throw new LockedException("登录失败次数过多，账户已锁定");
			throw new BadCredentialsException("用户名或密码错误");
		}
		user.recordSuccessfulLogin();
		return UserPrincipal.from(users.save(user));
	}

	@Transactional
	public UserPrincipal changePasswordWithDigest(UserPrincipal principal, String currentDigest, String newDigest) {
		AppUser user = users.findById(principal.id())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账户不存在"));
		if (!passwordHashService.matchesDigest(currentDigest == null ? "" : currentDigest, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前密码不正确");
		}
		if (newDigest == null || newDigest.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能为空");
		}
		if (passwordHashService.matchesDigest(newDigest, user.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
		}
		user.changePassword(passwordHashService.encodeDigest(newDigest));
		return UserPrincipal.from(users.save(user));
	}
}
