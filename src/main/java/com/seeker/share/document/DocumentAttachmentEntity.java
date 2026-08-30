package com.seeker.share.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_attachments")
public class DocumentAttachmentEntity {

	@Id
	private UUID id;

	@Column(name = "doc_id", nullable = false)
	private UUID docId;

	@Column(name = "file_name", nullable = false, length = 512)
	private String fileName;

	@Column(name = "content_type", length = 150)
	private String contentType;

	@Column(nullable = false)
	private long size;

	@Column(name = "stored_name", nullable = false, length = 128)
	private String storedName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "created_by", length = 64)
	private String createdBy;

	protected DocumentAttachmentEntity() { }

	public DocumentAttachmentEntity(UUID docId, String fileName, String contentType, long size, String storedName, String createdBy) {
		this.id = UUID.randomUUID();
		this.docId = docId;
		this.fileName = fileName;
		this.contentType = contentType;
		this.size = size;
		this.storedName = storedName;
		this.createdAt = Instant.now();
		this.createdBy = createdBy;
	}

	public UUID getId() { return id; }
	public UUID getDocId() { return docId; }
	public String getFileName() { return fileName; }
	public String getContentType() { return contentType; }
	public long getSize() { return size; }
	public String getStoredName() { return storedName; }
	public Instant getCreatedAt() { return createdAt; }
	public String getCreatedBy() { return createdBy; }
}
