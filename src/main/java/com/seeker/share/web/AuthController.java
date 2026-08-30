package com.seeker.share.web;

import com.seeker.share.common.ApiResponse;
import com.seeker.share.security.AppUser;
import com.seeker.share.security.AppUserRepository;
import com.seeker.share.security.AuthService;
import com.seeker.share.security.AuthStatus;
import com.seeker.share.security.ChangePasswordRequest;
import com.seeker.share.security.LoginRequest;
import com.seeker.share.security.PasswordHashService;
import com.seeker.share.security.PreloginNonceStore;
import com.seeker.share.security.RsaCipherService;
import com.seeker.share.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;
	private final SecurityContextRepository contextRepository;
	private final RsaCipherService rsaCipherService;
	private final PreloginNonceStore nonceStore;
	private final AppUserRepository users;
	private final PasswordHashService passwordHashService;

	public AuthController(AuthService authService, SecurityContextRepository contextRepository,
			RsaCipherService rsaCipherService, PreloginNonceStore nonceStore,
			AppUserRepository users, PasswordHashService passwordHashService) {
		this.authService = authService;
		this.contextRepository = contextRepository;
		this.rsaCipherService = rsaCipherService;
		this.nonceStore = nonceStore;
		this.users = users;
		this.passwordHashService = passwordHashService;
	}

	@GetMapping("/me")
	public ApiResponse<AuthStatus> me(Authentication authentication, CsrfToken csrfToken) {
		return ApiResponse.ok(status(authentication, csrfToken.getToken()));
	}

	@PostMapping("/prelogin")
	public ApiResponse<PreloginResponse> prelogin(@RequestBody PreloginRequest body) {
		String username = body.username() == null ? "" : body.username().strip().toLowerCase(Locale.ROOT);
		AppUser user = users.findByUsernameIgnoreCase(username).orElse(null);
		String salt = user != null ? user.getPasswordSalt() : null;
		if (salt == null) salt = passwordHashService.newSalt();
		String nonce = nonceStore.issue(salt);
		return ApiResponse.ok(new PreloginResponse(rsaCipherService.publicKeyBase64(), salt, nonce));
	}

	@PostMapping("/login")
	public ApiResponse<AuthStatus> login(
			@Valid @RequestBody LoginRequest body,
			HttpServletRequest request,
			HttpServletResponse response,
			CsrfToken csrfToken) {
		try {
			Credential credential = decryptCredential(body.credential());
			nonceStore.consume(credential.nonce());
			UserPrincipal principal = authService.loginWithDigest(body.username(), credential.digest());
			if (request.getSession(false) != null) request.changeSessionId();
			else request.getSession(true);
			Authentication authentication = principalAuthentication(principal);
			saveContext(authentication, request, response);
			return ApiResponse.ok(AuthStatus.authenticated(principal, csrfToken.getToken()));
		} catch (LockedException exception) {
			throw new ResponseStatusException(HttpStatus.LOCKED, exception.getMessage());
		} catch (BadCredentialsException exception) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, exception.getMessage());
		}
	}

	@PostMapping("/change-password")
	public ApiResponse<AuthStatus> changePassword(
			@Valid @RequestBody ChangePasswordRequest body,
			Authentication authentication,
			HttpServletRequest request,
			HttpServletResponse response,
			CsrfToken csrfToken) {
		UserPrincipal current = requirePrincipal(authentication);
		Credential currentCredential = decryptCredential(body.currentCredential());
		Credential newCredential = decryptCredential(body.newCredential());
		if (nonceStore.consume(currentCredential.nonce()) == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "登录凭据已失效，请刷新后重试");
		}
		if (nonceStore.consume(newCredential.nonce()) == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "登录凭据已失效，请刷新后重试");
		}
		UserPrincipal updated = authService.changePasswordWithDigest(
				current, currentCredential.digest(), newCredential.digest());
		Authentication replacement = principalAuthentication(updated);
		saveContext(replacement, request, response);
		return ApiResponse.ok(AuthStatus.authenticated(updated, csrfToken.getToken()));
	}

	@PostMapping("/logout")
	public ApiResponse<AuthStatus> logout(
			HttpServletRequest request,
			HttpServletResponse response,
			CsrfToken csrfToken) {
		SecurityContextHolder.clearContext();
		if (request.getSession(false) != null) request.getSession(false).invalidate();
		return ApiResponse.ok(AuthStatus.anonymous(csrfToken.getToken()));
	}

	private Credential decryptCredential(String encrypted) {
		try {
			String plain = rsaCipherService.decrypt(encrypted);
			String[] parts = plain.split(":");
			if (parts.length != 2) throw new IllegalArgumentException("格式错误");
			String digest = parts[0];
			String nonce = parts[1];
			if (!digest.matches("[0-9a-f]{64}") || !nonce.matches("[0-9a-f]{32}")) {
				throw new IllegalArgumentException("格式错误");
			}
			return new Credential(digest, nonce);
		} catch (IllegalArgumentException exception) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "登录凭据无效");
		}
	}

	private AuthStatus status(Authentication authentication, String csrfToken) {
		if (authentication != null && authentication.isAuthenticated()
				&& authentication.getPrincipal() instanceof UserPrincipal principal) {
			return AuthStatus.authenticated(principal, csrfToken);
		}
		return AuthStatus.anonymous(csrfToken);
	}

	private UserPrincipal requirePrincipal(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
		}
		return principal;
	}

	private Authentication principalAuthentication(UserPrincipal principal) {
		return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
				principal, null, principal.getAuthorities());
	}

	private void saveContext(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}

	private record Credential(String digest, String nonce) { }

	public record PreloginRequest(String username) { }

	public record PreloginResponse(String publicKey, String salt, String nonce) { }
}
