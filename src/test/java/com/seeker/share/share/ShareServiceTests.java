package com.seeker.share.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ShareServiceTests {

	@TempDir
	Path storage;

	private ShareService shareService;

	@BeforeEach
	void setUp() throws Exception {
		shareService = new ShareService(storage.toString(), 1024 * 1024, 24, new ShareEventService());
		shareService.initializeStorage();
	}

	@Test
	void sharesAndClearsMessages() throws Exception {
		ShareItem item = shareService.addMessage("  局域网消息  ");

		assertThat(item.content()).isEqualTo("局域网消息");
		assertThat(shareService.findAll()).containsExactly(item);

		shareService.clear();
		assertThat(shareService.findAll()).isEmpty();
	}

	@Test
	void storesDownloadsAndClearsFiles() throws Exception {
		MockMultipartFile upload = new MockMultipartFile(
				"file", "测试文件.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

		ShareItem item = shareService.addFile(upload);
		StoredFile stored = shareService.findFile(item.id());

		assertThat(item.fileName()).isEqualTo("测试文件.txt");
		assertThat(Files.readString(stored.path())).isEqualTo("hello");

		shareService.clear();
		assertThat(Files.exists(stored.path())).isFalse();
	}

	@Test
	void deletesOneItemWithoutClearingOthers() throws Exception {
		ShareItem first = shareService.addMessage("第一条");
		ShareItem second = shareService.addMessage("第二条");

		shareService.delete(first.id());

		assertThat(shareService.findAll()).containsExactly(second);
	}

	@Test
	void removesExpiredItems() throws Exception {
		ShareService expiringService = new ShareService(storage.toString(), 1024, 0, new ShareEventService());
		expiringService.initializeStorage();
		expiringService.addMessage("临时消息");

		expiringService.deleteExpired();

		assertThat(expiringService.findAll()).isEmpty();
	}
}
