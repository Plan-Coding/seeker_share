package com.seeker.share.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:auth-flow-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"seeker.security.admin-initial-password=ChangeMe!2026",
		"seeker.share.storage-location=${java.io.tmpdir}/seeker-share-auth-test"
})
@AutoConfigureMockMvc
class AuthenticationFlowTests {

	@Autowired MockMvc mvc;
	@Autowired ObjectMapper mapper;

	@Test
	void requiresLoginAndForcesInitialPasswordChange() throws Exception {
		mvc.perform(get("/api/v1/shares")).andExpect(status().isUnauthorized());

		String credential = loginCredential("admin", "ChangeMe!2026");
		MvcResult login = mvc.perform(post("/api/v1/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of(
						"username", "admin",
						"credential", credential))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.authenticated").value(true))
				.andExpect(jsonPath("$.data.passwordChangeRequired").value(true))
				.andReturn();

		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		mvc.perform(get("/api/v1/shares").session(session)).andExpect(status().isForbidden());

		String currentCredential = loginCredential("admin", "ChangeMe!2026");
		String newCredential = loginCredential("admin", "Violet-River#42");
		MvcResult changed = mvc.perform(post("/api/v1/auth/change-password").with(csrf())
				.session(session)
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of(
						"currentCredential", currentCredential,
						"newCredential", newCredential))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.passwordChangeRequired").value(false))
				.andReturn();

		mvc.perform(get("/api/v1/shares")
					.session((MockHttpSession) changed.getRequest().getSession(false)))
				.andExpect(status().isOk());
		mvc.perform(get("/api/v1/admin/roles")
					.session((MockHttpSession) changed.getRequest().getSession(false)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	void keepsToolApplicationShellPublic() throws Exception {
		mvc.perform(get("/")).andExpect(status().isOk());
		mvc.perform(post("/api/v1/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	private String loginCredential(String username, String password) throws Exception {
		MvcResult prelogin = mvc.perform(post("/api/v1/auth/prelogin").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(mapper.writeValueAsString(Map.of("username", username))))
				.andExpect(status().isOk())
				.andReturn();
		JsonNode data = mapper.readTree(prelogin.getResponse().getContentAsString()).path("data");
		return CredentialTestHelper.buildCredential(
				data.path("publicKey").asText(),
				data.path("salt").asText(),
				data.path("nonce").asText(),
				password);
	}
}
