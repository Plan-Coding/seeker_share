package com.seeker.share.share;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "share_entries")
public class ShareEntry {

	@Id
	private UUID id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ShareType type;

	@Column(length = 5000)
	private String content;

	@Column(name = "file_name", length = 512)
	private String fileName;

	@Column(name = "content_type", length = 150)
	private String contentType;

	@Column(nullable = false)
	private long size;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected ShareEntry() { }

	public ShareEntry(ShareItem item) {
		this.id = item.id();
		this.type = item.type();
		this.content = item.content();
		this.fileName = item.fileName();
		this.contentType = item.contentType();
		this.size = item.size();
		this.createdAt = item.createdAt();
		this.expiresAt = item.expiresAt();
	}

	public ShareItem toItem() {
		return new ShareItem(id, type, content, fileName, contentType, size, createdAt, expiresAt);
	}

	public UUID getId() { return id; }
	public Instant getExpiresAt() { return expiresAt; }
}
