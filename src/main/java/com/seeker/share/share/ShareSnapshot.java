package com.seeker.share.share;

import java.util.List;

public record ShareSnapshot(List<ShareItem> items, ShareStats stats) {
}
