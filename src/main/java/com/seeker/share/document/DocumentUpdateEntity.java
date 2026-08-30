package com.seeker.share.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * 协作编辑的增量更新日志。服务端只做「不透明的追加存储 + 中继」：
 * Yjs 增量更新对服务端透明,按文档顺序追加;新客户端加入时按 seq 重放,
 * CRDT 特性保证重放后状态收敛,无需在服务端运行 Yjs。
 */
@Entity
@Table(name = "document_updates")
public class DocumentUpdateEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "doc_id", nullable = false)
	private UUID docId;

	@Column(nullable = false)
	private long seq;

	@Lob
	@Column(nullable = false)
	private byte[] payload;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected DocumentUpdateEntity() { }

	public DocumentUpdateEntity(UUID docId, long seq, byte[] payload) {
		this.docId = docId;
		this.seq = seq;
		this.payload = payload;
		this.createdAt = Instant.now();
	}

	public Long getId() { return id; }
	public UUID getDocId() { return docId; }
	public long getSeq() { return seq; }
	public byte[] getPayload() { return payload; }
	public Instant getCreatedAt() { return createdAt; }
}
