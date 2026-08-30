package com.seeker.share.share;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:share-service-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class ShareServiceTests {

	@TempDir static Path storage;

	@DynamicPropertySource
	static void storageProperty(DynamicPropertyRegistry registry) {
		registry.add("seeker.share.storage-location", storage::toString);
	}

	@Autowired ShareService shareService;
	@Autowired ShareEntryRepository repository;

	@AfterEach
	void clean() throws Exception {
		shareService.clear();
	}

	@Test
	void persistsAndClearsMessages() throws Exception {
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
	void removesExpiredDatabaseEntries() throws Exception {
		Instant now = Instant.now();
		ShareItem expired = new ShareItem(UUID.randomUUID(), ShareType.MESSAGE, "临时消息", null, null,
				0, now.minusSeconds(20), now.minusSeconds(10));
		repository.save(new ShareEntry(expired));
		shareService.deleteExpired();
		assertThat(shareService.findAll()).isEmpty();
	}
}
