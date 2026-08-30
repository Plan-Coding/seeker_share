package com.seeker.share.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity {

	@Id
	private UUID id;

	@Column(nullable = false, length = 512)
	private String title;

	@Column(length = 128)
	private String category;

	@Column(length = 512)
	private String tags;

	@Lob
	@Column(nullable = false)
	private String content;

	@Lob
	@Column(name = "state_blob")
	private byte[] state;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "created_by", length = 64)
	private String createdBy;

	@Column(name = "updated_by", length = 64)
	private String updatedBy;

	protected DocumentEntity() { }

	public DocumentEntity(String title, String category, String tags, String content, String username) {
		this.id = UUID.randomUUID();
		this.title = title;
		this.category = normalizeCategory(category);
		this.tags = normalizeTags(tags);
		this.content = content;
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
		this.createdBy = username;
		this.updatedBy = username;
	}

	private static String normalizeCategory(String value) {
		return value == null ? null : value.trim().isEmpty() ? null : value.trim();
	}

	private static String normalizeTags(String value) {
		if (value == null) return null;
		return java.util.Arrays.stream(value.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.distinct()
				.limit(20)
				.reduce((a, b) -> a + "," + b)
				.orElse(null);
	}

	public void updateMetadata(String title, String category, String tags, String username) {
		this.title = title;
		this.category = normalizeCategory(category);
		this.tags = normalizeTags(tags);
		this.updatedBy = username;
		this.updatedAt = Instant.now();
	}

	public void touch(String username) {
		this.updatedBy = username;
		this.updatedAt = Instant.now();
	}

	public UUID getId() { return id; }
	public String getTitle() { return title; }
	public String getCategory() { return category; }
	public String getTags() { return tags; }
	public String getContent() { return content; }
	public byte[] getState() { return state; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public String getCreatedBy() { return createdBy; }
	public String getUpdatedBy() { return updatedBy; }

	public void setContent(String content) { this.content = content; }
	public void setState(byte[] state) { this.state = state; }
}
