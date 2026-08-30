package com.seeker.share.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:admin-management-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"seeker.share.storage-location=${java.io.tmpdir}/seeker-share-admin-management-test"
})
@AutoConfigureMockMvc
class AdminAccountManagementTests {

	@Autowired MockMvc mvc;
	@Autowired AppUserRepository users;
	@Autowired RoleRepository roles;
	@Autowired PermissionRepository permissions;

	@Test
	void createsUsersAndAssignsCustomRolesButProtectsCurrentOperator() throws Exception {
		mvc.perform(post("/api/v1/admin/roles").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"AUDITOR_A","description":"审计员","permissions":["SHARE_READ"]}
						"""))
				.andExpect(status().isOk());

		mvc.perform(post("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"audit.user","initialPassword":"Audit-Start#2026","roles":["MEMBER"]}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.roles[0]").value("MEMBER"));

		UUID userId = users.findByUsernameIgnoreCase("audit.user").orElseThrow().getId();
		mvc.perform(put("/api/v1/admin/users/{id}/roles", userId).with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":[\"AUDITOR_A\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.roles[0]").value("AUDITOR_A"));

		UUID adminId = users.findByUsernameIgnoreCase("admin").orElseThrow().getId();
		mvc.perform(put("/api/v1/admin/users/{id}/roles", adminId).with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"roles\":[\"MEMBER\"]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectsDeletingBuiltInOrAssignedRolesAndDeletesUnusedCustomRole() throws Exception {
		mvc.perform(post("/api/v1/admin/roles").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"TEMP_DELETE","description":"临时角色","permissions":[]}
						"""))
				.andExpect(status().isOk());
		UUID roleId = roles.findByName("TEMP_DELETE").orElseThrow().getId();

		mvc.perform(post("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"temp.user","initialPassword":"Temp-Start#2026","roles":["TEMP_DELETE"]}
						"""))
				.andExpect(status().isOk());
		UUID userId = users.findByUsernameIgnoreCase("temp.user").orElseThrow().getId();

		mvc.perform(delete("/api/v1/admin/roles/{id}", roleId).with(admin()).with(csrf()))
				.andExpect(status().isConflict());
		mvc.perform(put("/api/v1/admin/users/{id}/roles", userId).with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"roles\":[\"MEMBER\"]}"))
				.andExpect(status().isOk());
		mvc.perform(delete("/api/v1/admin/roles/{id}", roleId).with(admin()).with(csrf()))
				.andExpect(status().isOk());

		UUID memberRoleId = roles.findByName("MEMBER").orElseThrow().getId();
		mvc.perform(delete("/api/v1/admin/roles/{id}", memberRoleId).with(admin()).with(csrf()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void userManagerCannotAssignRolesWithoutRoleManagementPermission() throws Exception {
		mvc.perform(post("/api/v1/admin/users").with(userManager()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"forbidden.user","initialPassword":"Forbidden#2026","roles":["ADMIN"]}
						"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void disablingAccountRevokesItsExistingAuthenticationImmediately() throws Exception {
		mvc.perform(post("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"active.user","initialPassword":"Active-Start#2026","roles":["MEMBER"]}
						"""))
				.andExpect(status().isOk());
		AppUser user = users.findByUsernameIgnoreCase("active.user").orElseThrow();
		user.changePassword(user.getPasswordHash());
		users.save(user);
		RequestPostProcessor activeUser = principal(UserPrincipal.from(user));

		mvc.perform(get("/api/v1/shares").with(activeUser)).andExpect(status().isOk());
		mvc.perform(patch("/api/v1/admin/users/{id}/status", user.getId()).with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false,\"unlock\":false}"))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/shares").with(activeUser)).andExpect(status().isUnauthorized());
	}

	private RequestPostProcessor admin() {
		AppUser user = users.findByUsernameIgnoreCase("admin").orElseThrow();
		if (user.isPasswordChangeRequired()) {
			user.changePassword(user.getPasswordHash());
			user = users.save(user);
		}
		UserPrincipal stored = UserPrincipal.from(user);
		UserPrincipal principal = new UserPrincipal(stored.id(), stored.username(), stored.password(), true, true,
				false, stored.roles(), stored.permissions());
		return principal(principal);
	}

	private RequestPostProcessor userManager() {
		RoleEntity role = roles.findByName("USER_MANAGER_TEST").orElseGet(() -> new RoleEntity("USER_MANAGER_TEST", "用户管理员"));
		role.getPermissions().clear();
		role.getPermissions().add(permissions.findById(PermissionCode.USER_MANAGE).orElseThrow());
		role = roles.save(role);
		AppUser user = users.findByUsernameIgnoreCase("user-manager")
				.orElseGet(() -> new AppUser("user-manager", "unused"));
		user.getRoles().clear();
		user.getRoles().add(role);
		user.changePassword("unused");
		user = users.save(user);
		UserPrincipal principal = UserPrincipal.from(user);
		return principal(principal);
	}

	private RequestPostProcessor principal(UserPrincipal principal) {
		return authentication(new UsernamePasswordAuthenticationToken(
				principal, principal.getPassword(), principal.getAuthorities()));
	}
}
