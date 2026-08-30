package com.seeker.share.web;

import com.seeker.share.common.ApiResponse;
import com.seeker.share.security.AdminAccountService;
import com.seeker.share.security.AdminAccountService.PermissionSummary;
import com.seeker.share.security.AdminAccountService.RoleSummary;
import com.seeker.share.security.AdminAccountService.UserSummary;
import com.seeker.share.security.UserPrincipal;
import com.seeker.share.security.PermissionCode;
import com.seeker.share.security.PreloginNonceStore;
import com.seeker.share.security.RsaCipherService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminAccountController {

	private final AdminAccountService accounts;
	private final RsaCipherService rsaCipherService;
	private final PreloginNonceStore nonceStore;

	public AdminAccountController(AdminAccountService accounts, RsaCipherService rsaCipherService,
			PreloginNonceStore nonceStore) {
		this.accounts = accounts;
		this.rsaCipherService = rsaCipherService;
		this.nonceStore = nonceStore;
	}

	@GetMapping("/users")
	public ApiResponse<List<UserSummary>> users() { return ApiResponse.ok(accounts.users()); }

	@DeleteMapping("/users")
	public ApiResponse<AdminAccountService.DeleteUsersResult> deleteUsers(@Valid @RequestBody DeleteUsersRequest body,
			Authentication authentication) {
		UserPrincipal operator = (UserPrincipal) authentication.getPrincipal();
		return ApiResponse.ok(accounts.deleteUsers(body.ids(), operator.id()));
	}

	@PostMapping("/users")
	public ApiResponse<UserSummary> createUser(@Valid @RequestBody CreateUserRequest body, Authentication authentication) {
		boolean canManageRoles = authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_MANAGE"));
		Credential credential = decryptCredential(body.credential());
		String salt = nonceStore.consume(credential.nonce());
		if (salt == null) throw new org.springframework.web.server.ResponseStatusException(
				org.springframework.http.HttpStatus.BAD_REQUEST, "登录凭据已失效，请刷新后重试");
		return ApiResponse.ok(accounts.createUser(body.username(), salt, credential.digest(), body.roles(), canManageRoles));
	}

	@PatchMapping("/users/{id}/status")
	public ApiResponse<UserSummary> updateStatus(@PathVariable UUID id, @RequestBody UserStatusRequest body,
			Authentication authentication) {
		UserPrincipal operator = (UserPrincipal) authentication.getPrincipal();
		return ApiResponse.ok(accounts.updateStatus(id, operator.id(), body.enabled(), body.unlock()));
	}

	@PostMapping("/users/{id}/reset-password")
	public ApiResponse<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest body) {
		Credential credential = decryptCredential(body.credential());
		if (nonceStore.consume(credential.nonce()) == null) throw new org.springframework.web.server.ResponseStatusException(
				org.springframework.http.HttpStatus.BAD_REQUEST, "登录凭据已失效，请刷新后重试");
		accounts.resetPassword(id, credential.digest());
		return ApiResponse.ok(null);
	}

	@PutMapping("/users/{id}/roles")
	public ApiResponse<UserSummary> updateRoles(@PathVariable UUID id, @Valid @RequestBody UserRolesRequest body,
			Authentication authentication) {
		UserPrincipal operator = (UserPrincipal) authentication.getPrincipal();
		return ApiResponse.ok(accounts.updateRoles(id, operator.id(), body.roles()));
	}

	@GetMapping("/roles")
	public ApiResponse<List<RoleSummary>> roles() { return ApiResponse.ok(accounts.roles()); }

	@GetMapping("/permissions")
	public ApiResponse<List<PermissionSummary>> permissions() { return ApiResponse.ok(accounts.permissions()); }

	@PostMapping("/roles")
	public ApiResponse<RoleSummary> createRole(@Valid @RequestBody RoleRequest body) {
		return ApiResponse.ok(accounts.createRole(body.name(), body.description(), body.permissions()));
	}

	@PutMapping("/roles/{id}")
	public ApiResponse<RoleSummary> updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest body) {
		return ApiResponse.ok(accounts.updateRole(id, body.description(), body.permissions()));
	}

	@DeleteMapping("/roles/{id}")
	public ApiResponse<Void> deleteRole(@PathVariable UUID id) {
		accounts.deleteRole(id);
		return ApiResponse.ok(null);
	}

	public record CreateUserRequest(
			@NotBlank(message = "用户名不能为空")
			@Pattern(regexp = "^[a-zA-Z0-9_.-]{3,50}$", message = "用户名只能包含字母、数字、点、横线和下划线，长度 3-50 位") String username,
			@NotBlank(message = "初始密码不能为空") String credential,
			Set<String> roles) {
		public CreateUserRequest {
			username = username == null ? null : username.strip();
		}
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
			throw new org.springframework.web.server.ResponseStatusException(
					org.springframework.http.HttpStatus.BAD_REQUEST, "登录凭据无效");
		}
	}

	private record Credential(String digest, String nonce) { }

	public record DeleteUsersRequest(Set<UUID> ids) {
		public DeleteUsersRequest { ids = ids == null ? Set.of() : Set.copyOf(ids); }
	}

	public record UserStatusRequest(Boolean enabled, boolean unlock) { }
	public record UserRolesRequest(Set<String> roles) {
		public UserRolesRequest { roles = roles == null ? Set.of() : Set.copyOf(roles); }
	}
	public record ResetPasswordRequest(
			@NotBlank(message = "初始密码不能为空") String credential) { }
	public record RoleRequest(
			@NotBlank(message = "角色名不能为空")
			@Pattern(regexp = "^[A-Z][A-Z0-9_]{2,49}$", message = "角色名只能包含大写字母、数字和下划线，以字母开头，长度 3-50 位") String name,
			@NotBlank(message = "角色描述不能为空") @Size(max = 100, message = "角色描述不能超过 100 字") String description,
			Set<PermissionCode> permissions) {
		public RoleRequest {
			permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		}
	}
	public record UpdateRoleRequest(
			@NotBlank(message = "角色描述不能为空") @Size(max = 100, message = "角色描述不能超过 100 字") String description,
			Set<PermissionCode> permissions) {
		public UpdateRoleRequest {
			permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		}
	}
}
