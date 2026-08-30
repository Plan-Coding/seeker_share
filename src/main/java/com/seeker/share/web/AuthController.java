package com.seeker.share.web;

import com.seeker.share.common.ApiResponse;
import com.seeker.share.security.AuthService;
import com.seeker.share.security.AuthStatus;
import com.seeker.share.security.ChangePasswordRequest;
import com.seeker.share.security.LoginRequest;
import com.seeker.share.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
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

	public AuthController(AuthService authService, SecurityContextRepository contextRepository) {
		this.authService = authService;
		this.contextRepository = contextRepository;
	}

	@GetMapping("/me")
	public ApiResponse<AuthStatus> me(Authentication authentication, CsrfToken csrfToken) {
		return ApiResponse.ok(status(authentication, csrfToken.getToken()));
	}

	@PostMapping("/login")
	public ApiResponse<AuthStatus> login(
			@Valid @RequestBody LoginRequest body,
			HttpServletRequest request,
			HttpServletResponse response,
			CsrfToken csrfToken) {
		try {
			UserPrincipal principal = authService.login(body.username(), body.password());
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
		UserPrincipal updated = authService.changePassword(
				current, body.currentPassword(), body.newPassword(), body.confirmation());
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
}
