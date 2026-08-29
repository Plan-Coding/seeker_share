package com.seeker.share.share;

import java.time.Instant;
import java.util.UUID;

public record ShareItem(
		UUID id,
		ShareType type,
		String content,
		String fileName,
		String contentType,
		long size,
		Instant createdAt,
		Instant expiresAt) {
}
