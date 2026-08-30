package com.seeker.share.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccountStateRefreshFilter extends OncePerRequestFilter {

	private final AppUserRepository users;

	public AccountStateRefreshFilter(AppUserRepository users) {
		this.users = users;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal current) {
			users.findById(current.id()).map(UserPrincipal::from).ifPresentOrElse(updated -> {
				if (!updated.enabled() || !updated.accountNonLocked()) {
					SecurityContextHolder.clearContext();
					return;
				}
				UsernamePasswordAuthenticationToken refreshed = UsernamePasswordAuthenticationToken.authenticated(
						updated, updated.getPassword(), updated.getAuthorities());
				refreshed.setDetails(authentication.getDetails());
				SecurityContextHolder.getContext().setAuthentication(refreshed);
			}, SecurityContextHolder::clearContext);
		}
		filterChain.doFilter(request, response);
	}
}
