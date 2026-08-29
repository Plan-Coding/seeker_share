package com.seeker.share.web;

import java.time.Instant;
import java.util.List;

public record ServerInfo(String hostName, List<String> accessUrls, boolean clearProtected, Instant startedAt) {
}
