package com.seeker.share.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
	@Autowired ObjectMapper mapper;

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
				.content(mapper.writeValueAsString(Map.of(
						"username", "audit.user",
						"credential", createUserCredential("audit.user", "Audit-Start#2026"),
						"roles", java.util.List.of("MEMBER")))))
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
				.content(mapper.writeValueAsString(Map.of(
						"username", "temp.user",
						"credential", createUserCredential("temp.user", "Temp-Start#2026"),
						"roles", java.util.List.of("TEMP_DELETE")))))
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
				.content(mapper.writeValueAsString(Map.of(
						"username", "forbidden.user",
						"credential", createUserCredential("forbidden.user", "Forbidden#2026"),
						"roles", java.util.List.of("ADMIN")))))
				.andExpect(status().isForbidden());
	}

	@Test
	void createUserValidationErrorsReturnChineseProblemDetail() throws Exception {
		mvc.perform(post("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"中文名\",\"credential\":\"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("用户名只能包含字母、数字、点、横线和下划线，长度 3-50 位"));

		mvc.perform(post("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"valid.name\",\"credential\":\"bad\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("登录凭据无效"));

		mvc.perform(post("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("not json"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("请求体格式错误，请检查请求内容"));
	}

	@Test
	void disablingAccountRevokesItsExistingAuthenticationImmediately() throws Exception {
		mvc.perform(post("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of(
						"username", "active.user",
						"credential", createUserCredential("active.user", "Active-Start#2026"),
						"roles", java.util.List.of("MEMBER")))))
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

	@Test
	void deletesUsersInBatchButProtectsSelfAndBuiltInAdmin() throws Exception {
		mvc.perform(post("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of(
						"username", "batch.a",
						"credential", createUserCredential("batch.a", "Batch-A#2026"),
						"roles", java.util.List.of("MEMBER")))))
				.andExpect(status().isOk());
		mvc.perform(post("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of(
						"username", "batch.b",
						"credential", createUserCredential("batch.b", "Batch-B#2026"),
						"roles", java.util.List.of("MEMBER")))))
				.andExpect(status().isOk());
		UUID a = users.findByUsernameIgnoreCase("batch.a").orElseThrow().getId();
		UUID b = users.findByUsernameIgnoreCase("batch.b").orElseThrow().getId();

		mvc.perform(delete("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ids\":[\"%s\",\"%s\"]}".formatted(a, b)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.deleted").value(2));
		assertTrue(users.findByUsernameIgnoreCase("batch.a").isEmpty());
		assertTrue(users.findByUsernameIgnoreCase("batch.b").isEmpty());

		UUID adminId = users.findByUsernameIgnoreCase("admin").orElseThrow().getId();
		mvc.perform(delete("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ids\":[\"%s\"]}".formatted(adminId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("不能删除当前登录账户"));

		mvc.perform(delete("/api/v1/admin/users").with(userManager()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ids\":[\"%s\"]}".formatted(adminId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("内置管理员账户不可删除"));

		mvc.perform(delete("/api/v1/admin/users").with(admin()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"ids\":[]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("请选择要删除的用户"));
	}

	private String createUserCredential(String username, String password) throws Exception {
		var result = mvc.perform(post("/api/v1/auth/prelogin").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("username", username))))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode data = mapper.readTree(result.getResponse().getContentAsString()).path("data");
		return CredentialTestHelper.buildCredential(
				data.path("publicKey").asText(),
				data.path("salt").asText(),
				data.path("nonce").asText(),
				password);
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
