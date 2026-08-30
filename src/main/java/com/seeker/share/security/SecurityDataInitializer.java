package com.seeker.share.security;

import java.util.EnumMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SecurityDataInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SecurityDataInitializer.class);
	private final PermissionRepository permissions;
	private final RoleRepository roles;
	private final AppUserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final String adminUsername;
	private final String initialPassword;

	public SecurityDataInitializer(
			PermissionRepository permissions,
			RoleRepository roles,
			AppUserRepository users,
			PasswordEncoder passwordEncoder,
			@Value("${seeker.security.admin-username}") String adminUsername,
			@Value("${seeker.security.admin-initial-password}") String initialPassword) {
		this.permissions = permissions;
		this.roles = roles;
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.adminUsername = adminUsername.strip();
		this.initialPassword = initialPassword;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		Map<PermissionCode, String> descriptions = descriptions();
		for (PermissionCode code : PermissionCode.values()) {
			if (!permissions.existsById(code)) permissions.save(new PermissionEntity(code, descriptions.get(code)));
		}

		RoleEntity adminRole = roles.findByName("ADMIN")
				.orElseGet(() -> new RoleEntity("ADMIN", "系统管理员"));
		adminRole.getPermissions().clear();
		adminRole.getPermissions().addAll(permissions.findAll());
		adminRole = roles.save(adminRole);

		RoleEntity memberRole = roles.findByName("MEMBER")
				.orElseGet(() -> new RoleEntity("MEMBER", "共享成员"));
		memberRole.getPermissions().clear();
		memberRole.getPermissions().addAll(permissions.findAllById(java.util.List.of(
				PermissionCode.SHARE_READ, PermissionCode.SHARE_CREATE,
				PermissionCode.SHARE_DOWNLOAD, PermissionCode.DEVICE_READ)));
		roles.save(memberRole);

		if (!users.existsByUsernameIgnoreCase(adminUsername)) {
			AppUser admin = new AppUser(adminUsername, passwordEncoder.encode(initialPassword));
			admin.getRoles().add(adminRole);
			users.save(admin);
			log.warn("已初始化管理员账户 '{}'；首次登录必须修改初始密码", adminUsername);
		}
	}

	private Map<PermissionCode, String> descriptions() {
		Map<PermissionCode, String> values = new EnumMap<>(PermissionCode.class);
		values.put(PermissionCode.SHARE_READ, "查看共享内容");
		values.put(PermissionCode.SHARE_CREATE, "发布消息与文件");
		values.put(PermissionCode.SHARE_DOWNLOAD, "下载共享文件");
		values.put(PermissionCode.SHARE_DELETE, "删除单条共享内容");
		values.put(PermissionCode.SHARE_CLEAR, "清空全部共享内容");
		values.put(PermissionCode.DEVICE_READ, "查看在线设备");
		values.put(PermissionCode.USER_MANAGE, "管理用户账户");
		values.put(PermissionCode.ROLE_MANAGE, "管理角色与权限");
		return values;
	}
}
