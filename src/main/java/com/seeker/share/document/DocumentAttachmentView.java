package com.seeker.share.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentAttachmentView(
		UUID id,
		String fileName,
		String contentType,
		long size,
		Instant createdAt,
		String url) {

	public static DocumentAttachmentView of(DocumentAttachmentEntity attachment, String url) {
		return new DocumentAttachmentView(
				attachment.getId(),
				attachment.getFileName(),
				attachment.getContentType(),
				attachment.getSize(),
				attachment.getCreatedAt(),
				url);
	}
}
