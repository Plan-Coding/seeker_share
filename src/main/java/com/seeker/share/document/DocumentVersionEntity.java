package com.seeker.share.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_versions")
public class DocumentVersionEntity {

	@Id
	private UUID id;

	@Column(name = "doc_id", nullable = false)
	private UUID docId;

	@Column(name = "version_no", nullable = false)
	private int versionNo;

	@Column(nullable = false, length = 512)
	private String title;

	@Lob
	@Column(nullable = false)
	private String content;

	@Lob
	@Column(name = "state_blob")
	private byte[] state;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "created_by", length = 64)
	private String createdBy;

	protected DocumentVersionEntity() { }

	public DocumentVersionEntity(DocumentEntity document, int versionNo, String username) {
		this.id = UUID.randomUUID();
		this.docId = document.getId();
		this.versionNo = versionNo;
		this.title = document.getTitle();
		this.content = document.getContent();
		this.state = document.getState();
		this.createdAt = Instant.now();
		this.createdBy = username;
	}

	public UUID getId() { return id; }
	public UUID getDocId() { return docId; }
	public int getVersionNo() { return versionNo; }
	public String getTitle() { return title; }
	public String getContent() { return content; }
	public byte[] getState() { return state; }
	public Instant getCreatedAt() { return createdAt; }
	public String getCreatedBy() { return createdBy; }
}
