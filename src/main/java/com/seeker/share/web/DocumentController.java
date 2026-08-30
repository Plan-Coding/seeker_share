package com.seeker.share.web;

import com.seeker.share.common.ApiResponse;
import com.seeker.share.document.DocumentAttachmentView;
import com.seeker.share.document.DocumentDetail;
import com.seeker.share.document.DocumentSaveRequest;
import com.seeker.share.document.DocumentService;
import com.seeker.share.document.DocumentSummary;
import com.seeker.share.document.DocumentVersionView;
import com.seeker.share.document.StoredDocumentAttachment;
import com.seeker.share.security.UserPrincipal;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

	private final DocumentService documents;

	public DocumentController(DocumentService documents) {
		this.documents = documents;
	}

	private static String username(Authentication authentication) {
		return ((UserPrincipal) authentication.getPrincipal()).getUsername();
	}

	@GetMapping
	public ApiResponse<List<DocumentSummary>> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) String category) {
		return ApiResponse.ok(documents.list(q, category));
	}

	@GetMapping("/categories")
	public ApiResponse<List<String>> categories() {
		return ApiResponse.ok(documents.categories());
	}

	@GetMapping("/{id}")
	public ApiResponse<DocumentDetail> get(@PathVariable UUID id) {
		return ApiResponse.ok(documents.get(id));
	}

	@PostMapping
	public ResponseEntity<ApiResponse<DocumentDetail>> create(
			@Valid @RequestBody DocumentSaveRequest request,
			@RequestParam(required = false) String state,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(documents.create(request, decodeState(state), username(authentication))));
	}

	@PutMapping("/{id}")
	public ApiResponse<DocumentDetail> update(
			@PathVariable UUID id,
			@Valid @RequestBody DocumentSaveRequest request,
			@RequestParam(required = false) String state,
			Authentication authentication) {
		return ApiResponse.ok(documents.update(id, request, decodeState(state), username(authentication)));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable UUID id) {
		documents.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/versions")
	public ApiResponse<List<DocumentVersionView>> versions(@PathVariable UUID id) {
		return ApiResponse.ok(documents.versions(id));
	}

	@PostMapping("/{id}/versions")
	public ApiResponse<DocumentVersionView> createVersion(@PathVariable UUID id, Authentication authentication) {
		return ApiResponse.ok(documents.createVersion(id, username(authentication)));
	}

	@PostMapping("/{id}/rollback/{versionId}")
	public ApiResponse<DocumentDetail> rollback(
			@PathVariable UUID id,
			@PathVariable UUID versionId,
			Authentication authentication) {
		return ApiResponse.ok(documents.rollback(id, versionId, username(authentication)));
	}

	@GetMapping("/{id}/attachments")
	public ApiResponse<List<DocumentAttachmentView>> attachments(@PathVariable UUID id) {
		return ApiResponse.ok(documents.attachments(id));
	}

	@PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<DocumentAttachmentView>> uploadAttachment(
			@PathVariable UUID id,
			@RequestParam("file") MultipartFile file,
			Authentication authentication) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(documents.saveAttachment(id, file, username(authentication))));
	}

	@GetMapping("/attachments/{attachmentId}")
	public ResponseEntity<Resource> downloadAttachment(@PathVariable UUID attachmentId) {
		StoredDocumentAttachment stored = documents.loadAttachment(attachmentId);
		Resource resource = new FileSystemResource(stored.path());
		String encoded = java.net.URLEncoder.encode(stored.attachment().getFileName(), StandardCharsets.UTF_8)
				.replace("+", "%20");
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(stored.attachment().getContentType() == null
						? MediaType.APPLICATION_OCTET_STREAM_VALUE : stored.attachment().getContentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.inline().filename(stored.attachment().getFileName(), StandardCharsets.UTF_8).build().toString())
				.header("X-File-Name", encoded)
				.body(resource);
	}

	private static byte[] decodeState(String state) {
		if (state == null || state.isBlank()) return null;
		return java.util.Base64.getDecoder().decode(state);
	}
}
