package com.seeker.share.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 50)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 100)
	private String passwordHash;

	@Column(name = "password_salt", length = 32)
	private String passwordSalt;

	@Column(nullable = false)
	private boolean enabled = true;

	@Column(name = "account_non_locked", nullable = false)
	private boolean accountNonLocked = true;

	@Column(name = "password_change_required", nullable = false)
	private boolean passwordChangeRequired = true;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "user_roles",
			joinColumns = @JoinColumn(name = "user_id"),
			inverseJoinColumns = @JoinColumn(name = "role_id"))
	private Set<RoleEntity> roles = new LinkedHashSet<>();

	protected AppUser() { }

	public AppUser(String username, String passwordHash) {
		this(username, passwordHash, null);
	}

	public AppUser(String username, String passwordHash, String passwordSalt) {
		this.username = username;
		this.passwordHash = passwordHash;
		this.passwordSalt = passwordSalt;
	}

	@PreUpdate
	void updateTimestamp() { updatedAt = Instant.now(); }

	public void recordFailedLogin(int maximumAttempts) {
		failedLoginAttempts++;
		if (failedLoginAttempts >= maximumAttempts) accountNonLocked = false;
	}

	public void recordSuccessfulLogin() {
		failedLoginAttempts = 0;
		lastLoginAt = Instant.now();
	}

	public void changePassword(String encodedPassword) {
		passwordHash = encodedPassword;
		passwordChangeRequired = false;
		passwordChangedAt = Instant.now();
		failedLoginAttempts = 0;
		accountNonLocked = true;
	}

	public void resetPassword(String encodedPassword) {
		passwordHash = encodedPassword;
		passwordChangeRequired = true;
		failedLoginAttempts = 0;
		accountNonLocked = true;
		passwordChangedAt = null;
	}

	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public void unlock() { failedLoginAttempts = 0; accountNonLocked = true; }

	public UUID getId() { return id; }
	public String getUsername() { return username; }
	public String getPasswordHash() { return passwordHash; }
	public String getPasswordSalt() { return passwordSalt; }
	public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }
	public boolean isEnabled() { return enabled; }
	public boolean isAccountNonLocked() { return accountNonLocked; }
	public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
	public int getFailedLoginAttempts() { return failedLoginAttempts; }
	public Instant getLastLoginAt() { return lastLoginAt; }
	public Instant getCreatedAt() { return createdAt; }
	public Set<RoleEntity> getRoles() { return roles; }
}
