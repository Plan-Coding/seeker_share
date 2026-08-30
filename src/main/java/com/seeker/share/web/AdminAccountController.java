package com.seeker.share.web;

import com.seeker.share.common.ApiResponse;
import com.seeker.share.security.AdminAccountService;
import com.seeker.share.security.AdminAccountService.PermissionSummary;
import com.seeker.share.security.AdminAccountService.RoleSummary;
import com.seeker.share.security.AdminAccountService.UserSummary;
import com.seeker.share.security.UserPrincipal;
import com.seeker.share.security.PermissionCode;
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

	public AdminAccountController(AdminAccountService accounts) { this.accounts = accounts; }

	@GetMapping("/users")
	public ApiResponse<List<UserSummary>> users() { return ApiResponse.ok(accounts.users()); }

	@PostMapping("/users")
	public ApiResponse<UserSummary> createUser(@Valid @RequestBody CreateUserRequest body, Authentication authentication) {
		boolean canManageRoles = authentication.getAuthorities().stream()
				.anyMatch(authority -> authority.getAuthority().equals("ROLE_MANAGE"));
		return ApiResponse.ok(accounts.createUser(body.username(), body.initialPassword(), body.roles(), canManageRoles));
	}

	@PatchMapping("/users/{id}/status")
	public ApiResponse<UserSummary> updateStatus(@PathVariable UUID id, @RequestBody UserStatusRequest body,
			Authentication authentication) {
		UserPrincipal operator = (UserPrincipal) authentication.getPrincipal();
		return ApiResponse.ok(accounts.updateStatus(id, operator.id(), body.enabled(), body.unlock()));
	}

	@PostMapping("/users/{id}/reset-password")
	public ApiResponse<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest body) {
		accounts.resetPassword(id, body.initialPassword());
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
			@NotBlank @Pattern(regexp = "^[a-zA-Z0-9_.-]{3,50}$", message = "用户名只能包含字母、数字、点、横线和下划线") String username,
			@NotBlank @Size(max = 128) String initialPassword,
			Set<String> roles) { }

	public record UserStatusRequest(Boolean enabled, boolean unlock) { }
	public record UserRolesRequest(Set<String> roles) {
		public UserRolesRequest { roles = roles == null ? Set.of() : Set.copyOf(roles); }
	}
	public record ResetPasswordRequest(@NotBlank @Size(max = 128) String initialPassword) { }
	public record RoleRequest(
			@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,49}$") String name,
			@NotBlank @Size(max = 100) String description,
			Set<PermissionCode> permissions) {
		public RoleRequest {
			permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		}
	}
	public record UpdateRoleRequest(
			@NotBlank @Size(max = 100) String description,
			Set<PermissionCode> permissions) {
		public UpdateRoleRequest {
			permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
		}
	}
}
