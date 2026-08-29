package com.seeker.share.web;

import com.seeker.share.common.ApiResponse;
import com.seeker.share.share.AdminGuard;
import com.seeker.share.share.MessageRequest;
import com.seeker.share.share.ShareEventService;
import com.seeker.share.share.ShareItem;
import com.seeker.share.share.ShareService;
import com.seeker.share.share.ShareSnapshot;
import com.seeker.share.share.StoredFile;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/shares")
public class ShareController {

	private final ShareService shareService;
	private final ShareEventService events;
	private final AdminGuard adminGuard;

	public ShareController(ShareService shareService, ShareEventService events, AdminGuard adminGuard) {
		this.shareService = shareService;
		this.events = events;
		this.adminGuard = adminGuard;
	}

	@GetMapping
	public ApiResponse<ShareSnapshot> list() {
		return ApiResponse.ok(shareService.snapshot());
	}

	@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events() {
		return events.connect();
	}

	@PostMapping("/messages")
	public ResponseEntity<ApiResponse<ShareItem>> addMessage(@Valid @RequestBody MessageRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(shareService.addMessage(request.content())));
	}

	@PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<ShareItem>> addFile(@RequestParam MultipartFile file) throws IOException {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(shareService.addFile(file)));
	}

	@GetMapping("/files/{id}")
	public ResponseEntity<Resource> download(@PathVariable UUID id) {
		StoredFile storedFile = shareService.findFile(id);
		ShareItem item = storedFile.item();
		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(item.fileName(), StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.contentLength(item.size())
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(new FileSystemResource(storedFile.path()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@PathVariable UUID id,
			@RequestHeader(name = "X-Admin-Token", required = false) String token) throws IOException {
		adminGuard.verify(token);
		shareService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> clear(
			@RequestHeader(name = "X-Admin-Token", required = false) String token) throws IOException {
		adminGuard.verify(token);
		shareService.clear();
		return ResponseEntity.noContent().build();
	}
}
