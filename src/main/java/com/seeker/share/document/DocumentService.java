package com.seeker.share.document;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * 文档库业务：CRUD、搜索、版本快照与回滚、附件存储、协作增量日志。
 * 协作部分只做「不透明存储 + 重放」：Yjs 增量对服务端透明。
 */
@Service
public class DocumentService {

	private final DocumentRepository documents;
	private final DocumentVersionRepository versions;
	private final DocumentUpdateRepository updates;
	private final DocumentAttachmentRepository attachments;
	private final Path storageLocation;

	public DocumentService(
			@Value("${seeker.documents.storage-location}") String storageLocation,
			DocumentRepository documents,
			DocumentVersionRepository versions,
			DocumentUpdateRepository updates,
			DocumentAttachmentRepository attachments) {
		this.storageLocation = Path.of(storageLocation).toAbsolutePath().normalize();
		this.documents = documents;
		this.versions = versions;
		this.updates = updates;
		this.attachments = attachments;
	}

	@PostConstruct
	void initializeStorage() throws IOException {
		Files.createDirectories(storageLocation);
	}

	private DocumentEntity require(UUID id) {
		return documents.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "文档不存在"));
	}

	private int nextVersionNo(UUID docId) {
		return versions.findTopByDocIdOrderByVersionNoDesc(docId)
				.map(DocumentVersionEntity::getVersionNo)
				.orElse(0) + 1;
	}

	/* ---------- CRUD ---------- */

	@Transactional
	public DocumentDetail create(DocumentSaveRequest request, byte[] state, String username) {
		DocumentEntity document = new DocumentEntity(
				request.title().strip(),
				request.category(),
				request.tags(),
				request.content() == null ? "" : request.content(),
				username);
		document.setState(state);
		documents.save(document);
		versions.save(new DocumentVersionEntity(document, 1, username));
		return DocumentDetail.of(document, 1);
	}

	@Transactional
	public DocumentDetail update(UUID id, DocumentSaveRequest request, byte[] state, String username) {
		DocumentEntity document = require(id);
		document.updateMetadata(request.title().strip(), request.category(), request.tags(), username);
		document.setContent(request.content() == null ? "" : request.content());
		document.setState(state);
		documents.save(document);
		checkpoint(document, username);
		return DocumentDetail.of(document, nextVersionNo(id) - 1);
	}

	/** 协作保存(WS 触发)：仅更新正文与状态并压缩更新日志,元数据不变。 */
	@Transactional
	public void saveCollaborative(UUID id, String content, byte[] state, String username) {
		DocumentEntity document = require(id);
		document.setContent(content == null ? "" : content);
		document.setState(state);
		document.touch(username);
		documents.save(document);
		checkpoint(document, username);
	}

	@Transactional(readOnly = true)
	public List<DocumentSummary> list(String q, String category) {
		String query = q == null || q.isBlank() ? null : q.strip();
		String cat = category == null || category.isBlank() ? null : category.strip();
		List<DocumentEntity> found = documents.search(query, cat);
		return found.stream().map(d -> DocumentSummary.of(d, nextVersionNo(d.getId()) - 1)).toList();
	}

	@Transactional(readOnly = true)
	public List<String> categories() {
		return documents.findDistinctCategories();
	}

	@Transactional(readOnly = true)
	public DocumentDetail get(UUID id) {
		DocumentEntity document = require(id);
		return DocumentDetail.of(document, nextVersionNo(id) - 1);
	}

	@Transactional(readOnly = true)
	public boolean exists(UUID id) {
		return documents.existsById(id);
	}

	@Transactional
	public void delete(UUID id) {
		DocumentEntity document = require(id);
		for (DocumentAttachmentEntity attachment : attachments.findByDocIdOrderByCreatedAtDesc(id)) {
			deleteFile(attachment.getStoredName());
		}
		attachments.deleteByDocId(id);
		updates.deleteByDocId(id);
		versions.deleteByDocId(id);
		documents.delete(document);
	}

	/* ---------- 版本历史 ---------- */

	@Transactional
	public DocumentVersionView createVersion(UUID id, String username) {
		DocumentEntity document = require(id);
		int no = nextVersionNo(id);
		DocumentVersionEntity version = versions.save(new DocumentVersionEntity(document, no, username));
		return DocumentVersionView.of(version);
	}

	@Transactional(readOnly = true)
	public List<DocumentVersionView> versions(UUID id) {
		require(id);
		return versions.findByDocIdOrderByVersionNoDesc(id).stream().map(DocumentVersionView::of).toList();
	}

	@Transactional
	public DocumentDetail rollback(UUID id, UUID versionId, String username) {
		DocumentEntity document = require(id);
		DocumentVersionEntity version = versions.findById(versionId)
				.filter(v -> v.getDocId().equals(id))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "版本不存在"));
		document.setContent(version.getContent());
		document.setState(version.getState());
		document.touch(username);
		documents.save(document);
		checkpoint(document, username);
		return DocumentDetail.of(document, nextVersionNo(id) - 1);
	}

	/* ---------- 附件 ---------- */

	@Transactional
	public DocumentAttachmentView saveAttachment(UUID id, MultipartFile file, String username) {
		require(id);
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未选择文件");
		}
		String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
		String ext = "";
		int dot = original.lastIndexOf('.');
		if (dot >= 0) ext = original.substring(dot).toLowerCase().replaceAll("[^a-z0-9.]", "");
		String storedName = UUID.randomUUID().toString().replace("-", "") + ext;
		Path target = storageLocation.resolve(storedName).normalize();
		if (!target.startsWith(storageLocation)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法文件名");
		}
		try (InputStream in = file.getInputStream()) {
			Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException error) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "附件保存失败");
		}
		DocumentAttachmentEntity attachment = attachments.save(new DocumentAttachmentEntity(
				id, original, file.getContentType(), file.getSize(), storedName, username));
		return DocumentAttachmentView.of(attachment, "/api/v1/documents/attachments/" + attachment.getId());
	}

	@Transactional(readOnly = true)
	public List<DocumentAttachmentView> attachments(UUID id) {
		require(id);
		return attachments.findByDocIdOrderByCreatedAtDesc(id).stream()
				.map(a -> DocumentAttachmentView.of(a, "/api/v1/documents/attachments/" + a.getId()))
				.toList();
	}

	@Transactional(readOnly = true)
	public StoredDocumentAttachment loadAttachment(UUID id) {
		DocumentAttachmentEntity attachment = attachments.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在"));
		Path file = storageLocation.resolve(attachment.getStoredName()).normalize();
		if (!file.startsWith(storageLocation) || !Files.exists(file)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件文件丢失");
		}
		return new StoredDocumentAttachment(attachment, file);
	}

	private void deleteFile(String storedName) {
		try {
			Path file = storageLocation.resolve(storedName).normalize();
			if (file.startsWith(storageLocation)) Files.deleteIfExists(file);
		} catch (IOException ignored) { }
	}

	/* ---------- 协作增量日志 ---------- */

	/** 追加一条协作增量,返回分配的 seq。 */
	@Transactional
	public long appendUpdate(UUID docId, byte[] payload) {
		require(docId);
		long seq = updates.findTopByDocIdOrderBySeqDesc(docId)
				.map(DocumentUpdateEntity::getSeq)
				.orElse(0L) + 1L;
		updates.save(new DocumentUpdateEntity(docId, seq, payload));
		return seq;
	}

	@Transactional(readOnly = true)
	public List<byte[]> replayUpdates(UUID docId) {
		return updates.findByDocIdOrderBySeqAsc(docId).stream().map(DocumentUpdateEntity::getPayload).toList();
	}

	@Transactional(readOnly = true)
	public byte[] currentState(UUID docId) {
		return require(docId).getState();
	}

	/** 压缩更新日志：以当前完整状态作为唯一基线,清空增量日志。 */
	@Transactional
	public void checkpoint(DocumentEntity document, String username) {
		updates.deleteByDocId(document.getId());
		if (document.getState() != null && document.getState().length > 0) {
			updates.save(new DocumentUpdateEntity(document.getId(), 1L, document.getState()));
		}
	}
}
