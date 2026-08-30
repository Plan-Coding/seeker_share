package com.seeker.share.share;

import java.time.Instant;

public record OnlineDevice(
		String id,
		String address,
		String name,
		String type,
		String browser,
		int connectionCount,
		Instant connectedAt,
		Instant lastSeen) {
}
