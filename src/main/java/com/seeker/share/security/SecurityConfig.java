package com.seeker.share.security;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(12);
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			SecurityContextRepository contextRepository,
			AccountStateRefreshFilter accountStateRefreshFilter) throws Exception {
		http
				.securityContext(context -> context
						.securityContextRepository(contextRepository)
						.requireExplicitSave(true))
				.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
						.requestMatchers("/api/v1/auth/me", "/api/v1/auth/login",
								"/api/v1/auth/change-password", "/api/v1/auth/logout",
								"/api/v1/server", "/actuator/health", "/actuator/info").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/shares/events", "/api/v1/shares").hasAuthority("SHARE_READ")
						.requestMatchers(HttpMethod.GET, "/api/v1/shares/files/**").hasAuthority("SHARE_DOWNLOAD")
						.requestMatchers(HttpMethod.POST, "/api/v1/shares/messages", "/api/v1/shares/files").hasAuthority("SHARE_CREATE")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/shares").hasAuthority("SHARE_CLEAR")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/shares/**").hasAuthority("SHARE_DELETE")
				.requestMatchers(HttpMethod.GET, "/api/v1/devices").hasAuthority("DEVICE_READ")
						.requestMatchers(HttpMethod.GET, "/api/v1/documents/**").hasAuthority("DOCUMENT_READ")
						.requestMatchers(HttpMethod.POST, "/api/v1/documents", "/api/v1/documents/*/versions",
								"/api/v1/documents/*/rollback/*", "/api/v1/documents/*/attachments").hasAuthority("DOCUMENT_WRITE")
						.requestMatchers(HttpMethod.PUT, "/api/v1/documents/**").hasAuthority("DOCUMENT_WRITE")
						.requestMatchers(HttpMethod.DELETE, "/api/v1/documents/**").hasAuthority("DOCUMENT_MANAGE")
						.requestMatchers("/ws/**").permitAll()
						.requestMatchers(HttpMethod.PUT, "/api/v1/admin/users/*/roles").hasAuthority("ROLE_MANAGE")
						.requestMatchers("/api/v1/admin/users/**").hasAuthority("USER_MANAGE")
						.requestMatchers("/api/v1/admin/roles/**", "/api/v1/admin/permissions").hasAuthority("ROLE_MANAGE")
						.requestMatchers("/api/**").denyAll()
						.anyRequest().permitAll())
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) -> jsonError(response, 401, "请先登录"))
						.accessDeniedHandler((request, response, exception) -> jsonError(response, 403, "没有执行此操作的权限")))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.logout(logout -> logout.disable());
		http.addFilterAfter(accountStateRefreshFilter, SecurityContextHolderFilter.class);
		return http.build();
	}

	private void jsonError(HttpServletResponse response, int status, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
	}
}
