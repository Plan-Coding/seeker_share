package com.seeker.share.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class PermissionEntity {

	@Id
	@Enumerated(EnumType.STRING)
	@Column(name = "code", length = 40)
	private PermissionCode code;

	@Column(nullable = false, length = 100)
	private String description;

	protected PermissionEntity() { }

	public PermissionEntity(PermissionCode code, String description) {
		this.code = code;
		this.description = description;
	}

	public PermissionCode getCode() { return code; }
	public String getDescription() { return description; }
}
