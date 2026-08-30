package com.seeker.share.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummary(
		UUID id,
		String title,
		String category,
		String tags,
		Instant updatedAt,
		String updatedBy,
		String createdBy,
		int versionNo) {

	public static DocumentSummary of(DocumentEntity document, int versionNo) {
		return new DocumentSummary(
				document.getId(),
				document.getTitle(),
				document.getCategory(),
				document.getTags(),
				document.getUpdatedAt(),
				document.getUpdatedBy(),
				document.getCreatedBy(),
				versionNo);
	}
}
