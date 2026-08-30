package com.seeker.share.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionView(
		UUID id,
		int versionNo,
		String title,
		Instant createdAt,
		String createdBy) {

	public static DocumentVersionView of(DocumentVersionEntity version) {
		return new DocumentVersionView(
				version.getId(),
				version.getVersionNo(),
				version.getTitle(),
				version.getCreatedAt(),
				version.getCreatedBy());
	}
}
