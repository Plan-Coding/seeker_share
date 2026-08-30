package com.seeker.share.security;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAccountService {
	private static final Set<String> BUILT_IN_ROLES = Set.of("ADMIN", "MEMBER");

	private final AppUserRepository users;
	private final RoleRepository roles;
	private final PermissionRepository permissions;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final String builtInAdminUsername;

	public AdminAccountService(AppUserRepository users, RoleRepository roles, PermissionRepository permissions,
			PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
			@Value("${seeker.security.admin-username}") String builtInAdminUsername) {
		this.users = users;
		this.roles = roles;
		this.permissions = permissions;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.builtInAdminUsername = builtInAdminUsername.strip();
	}

	@Transactional(readOnly = true)
	public List<UserSummary> users() {
		return users.findAll().stream().map(UserSummary::from)
				.sorted(Comparator.comparing(UserSummary::username)).toList();
	}

	@Transactional
	public UserSummary createUser(String username, String initialPassword, Set<String> roleNames, boolean canManageRoles) {
		String normalized = username.strip().toLowerCase(Locale.ROOT);
		if (users.existsByUsernameIgnoreCase(normalized)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
		}
		passwordPolicy.validate(initialPassword, normalized);
		Set<String> requestedRoles = normalizeRoleNames(roleNames == null || roleNames.isEmpty() ? Set.of("MEMBER") : roleNames);
		if (!canManageRoles && !requestedRoles.equals(Set.of("MEMBER"))) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "分配其他角色需要角色管理权限");
		}
		AppUser user = new AppUser(normalized, passwordEncoder.encode(initialPassword));
		user.getRoles().addAll(resolveRoles(requestedRoles));
		return UserSummary.from(users.save(user));
	}

	@Transactional
	public UserSummary updateRoles(UUID id, UUID operatorId, Set<String> roleNames) {
		if (id.equals(operatorId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能修改当前登录账户的角色");
		}
		Set<String> normalized = normalizeRoleNames(roleNames);
		if (normalized.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户至少需要一个角色");
		}
		AppUser user = requireUser(id);
		user.getRoles().clear();
		user.getRoles().addAll(resolveRoles(normalized));
		return UserSummary.from(users.save(user));
	}

	@Transactional
	public UserSummary updateStatus(UUID id, UUID operatorId, Boolean enabled, boolean unlock) {
		AppUser user = requireUser(id);
		if (id.equals(operatorId) && Boolean.FALSE.equals(enabled)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能停用当前登录账户");
		}
		if (enabled != null) user.setEnabled(enabled);
		if (unlock) user.unlock();
		return UserSummary.from(users.save(user));
	}

	@Transactional
	public void resetPassword(UUID id, String initialPassword) {
		AppUser user = requireUser(id);
		passwordPolicy.validate(initialPassword, user.getUsername());
		user.resetPassword(passwordEncoder.encode(initialPassword));
		users.save(user);
	}

	@Transactional(readOnly = true)
	public List<RoleSummary> roles() {
		return roles.findAll().stream().map(RoleSummary::from)
				.sorted(Comparator.comparing(RoleSummary::name)).toList();
	}

	@Transactional(readOnly = true)
	public List<PermissionSummary> permissions() {
		return permissions.findAll().stream()
				.map(value -> new PermissionSummary(value.getCode().name(), value.getDescription()))
				.sorted(Comparator.comparing(PermissionSummary::code)).toList();
	}

	@Transactional
	public DeleteUsersResult deleteUsers(Set<UUID> ids, UUID operatorId) {
		if (ids == null || ids.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要删除的用户");
		}
		if (ids.contains(operatorId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不能删除当前登录账户");
		}
		List<AppUser> targets = users.findAllById(ids);
		if (targets.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户不存在");
		}
		if (targets.stream().anyMatch(user -> user.getUsername().equalsIgnoreCase(builtInAdminUsername))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内置管理员账户不可删除");
		}
		users.deleteAll(targets);
		return new DeleteUsersResult(targets.size());
	}

	@Transactional
	public RoleSummary createRole(String name, String description, Set<PermissionCode> permissionCodes) {
		String normalized = name.strip().toUpperCase(Locale.ROOT);
		if (roles.findByName(normalized).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "角色已存在");
		}
		RoleEntity role = new RoleEntity(normalized, description.strip());
		role.getPermissions().addAll(permissions.findAllById(permissionCodes));
		return RoleSummary.from(roles.save(role));
	}

	@Transactional
	public RoleSummary updateRole(UUID id, String description, Set<PermissionCode> permissionCodes) {
		RoleEntity role = roles.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
		if (role.getName().equals("ADMIN")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "系统管理员角色不可修改");
		}
		role.update(role.getName(), description.strip());
		role.getPermissions().clear();
		role.getPermissions().addAll(permissions.findAllById(permissionCodes));
		return RoleSummary.from(roles.save(role));
	}

	@Transactional
	public void deleteRole(UUID id) {
		RoleEntity role = roles.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
		if (BUILT_IN_ROLES.contains(role.getName())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "内置角色不可删除");
		}
		if (users.countByRoles_Id(id) > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "该角色仍有用户使用，无法删除");
		}
		roles.delete(role);
	}

	private Set<String> normalizeRoleNames(Set<String> roleNames) {
		if (roleNames == null) return Set.of();
		return roleNames.stream().map(name -> name.strip().toUpperCase(Locale.ROOT))
				.filter(name -> !name.isEmpty()).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private Set<RoleEntity> resolveRoles(Set<String> roleNames) {
		return roleNames.stream().map(roleName -> roles.findByName(roleName)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在：" + roleName)))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private AppUser requireUser(UUID id) {
		return users.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "账户不存在"));
	}

	public record UserSummary(UUID id, String username, boolean enabled, boolean accountNonLocked,
			boolean passwordChangeRequired, int failedLoginAttempts, Set<String> roles,
			Instant createdAt, Instant lastLoginAt) {
		static UserSummary from(AppUser user) {
			return new UserSummary(user.getId(), user.getUsername(), user.isEnabled(), user.isAccountNonLocked(),
					user.isPasswordChangeRequired(), user.getFailedLoginAttempts(),
					user.getRoles().stream().map(RoleEntity::getName)
					.collect(Collectors.toSet()), user.getCreatedAt(), user.getLastLoginAt());
		}
	}

	public record RoleSummary(UUID id, String name, String description, boolean builtIn, Set<String> permissions) {
		static RoleSummary from(RoleEntity role) {
			return new RoleSummary(role.getId(), role.getName(), role.getDescription(),
					BUILT_IN_ROLES.contains(role.getName()), role.getPermissions().stream()
					.map(permission -> permission.getCode().name()).collect(Collectors.toSet()));
		}
	}

	public record PermissionSummary(String code, String description) { }

	public record DeleteUsersResult(int deleted) { }
}
