package com.seeker.share.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentDetail(
		UUID id,
		String title,
		String category,
		String tags,
		String content,
		Instant updatedAt,
		String updatedBy,
		String createdBy,
		int versionNo) {

	public static DocumentDetail of(DocumentEntity document, int versionNo) {
		return new DocumentDetail(
				document.getId(),
				document.getTitle(),
				document.getCategory(),
				document.getTags(),
				document.getContent(),
				document.getUpdatedAt(),
				document.getUpdatedBy(),
				document.getCreatedBy(),
				versionNo);
	}
}
