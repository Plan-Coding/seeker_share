package com.seeker.share.share;

import java.nio.file.Path;

public record StoredFile(ShareItem item, Path path) {
}
