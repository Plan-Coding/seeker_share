package com.seeker.share.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:account-lock-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop",
		"seeker.security.max-failed-attempts=3",
		"seeker.share.storage-location=${java.io.tmpdir}/seeker-share-lock-test"
})
class AccountLockTests {

	@Autowired AuthService authService;
	@Autowired AppUserRepository users;
	@Autowired PasswordHashService passwordHashService;

	@Test
	void locksAccountAfterConfiguredFailedAttempts() {
		AppUser admin = users.findByUsernameIgnoreCase("admin").orElseThrow();
		String salt = admin.getPasswordSalt();
		assertThatThrownBy(() -> authService.loginWithDigest("admin", passwordHashService.digest("wrong-one", salt)))
				.isInstanceOf(BadCredentialsException.class);
		assertThatThrownBy(() -> authService.loginWithDigest("admin", passwordHashService.digest("wrong-two", salt)))
				.isInstanceOf(BadCredentialsException.class);
		assertThatThrownBy(() -> authService.loginWithDigest("admin", passwordHashService.digest("wrong-three", salt)))
				.isInstanceOf(LockedException.class);

		admin = users.findByUsernameIgnoreCase("admin").orElseThrow();
		assertThat(admin.isAccountNonLocked()).isFalse();
		assertThat(admin.getFailedLoginAttempts()).isEqualTo(3);
	}
}
