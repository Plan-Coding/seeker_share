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
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class RoleEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;

	@Column(nullable = false, length = 100)
	private String description;

	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "role_permissions",
			joinColumns = @JoinColumn(name = "role_id"),
			inverseJoinColumns = @JoinColumn(name = "permission_code"))
	private Set<PermissionEntity> permissions = new LinkedHashSet<>();

	protected RoleEntity() { }

	public RoleEntity(String name, String description) {
		this.name = name;
		this.description = description;
	}

	public UUID getId() { return id; }
	public String getName() { return name; }
	public String getDescription() { return description; }
	public Set<PermissionEntity> getPermissions() { return permissions; }
	public void update(String name, String description) { this.name = name; this.description = description; }
}
