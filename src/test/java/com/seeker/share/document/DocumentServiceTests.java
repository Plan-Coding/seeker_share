package com.seeker.share.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:document-service-test;DB_CLOSE_DELAY=-1",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class DocumentServiceTests {

	@TempDir static java.nio.file.Path storage;

	@DynamicPropertySource
	static void storageProperty(DynamicPropertyRegistry registry) {
		registry.add("seeker.documents.storage-location", storage::toString);
	}

	@Autowired DocumentService documents;

	@Test
	void createsListsAndGetsDocument() {
		DocumentDetail created = documents.create(
				new DocumentSaveRequest("  需求文档  ", "  产品  ", "需求,文档", "# 标题\n正文内容"), null, "alice");
		assertThat(created.title()).isEqualTo("需求文档");
		assertThat(created.category()).isEqualTo("产品");
		assertThat(created.tags()).contains("需求", "文档");
		assertThat(created.versionNo()).isEqualTo(1);

		List<DocumentSummary> list = documents.list(null, null);
		assertThat(list).hasSize(1);
		assertThat(list.get(0).title()).isEqualTo("需求文档");

		DocumentDetail detail = documents.get(created.id());
		assertThat(detail.content()).isEqualTo("# 标题\n正文内容");
		assertThat(documents.categories()).containsExactly("产品");
	}

	@Test
	void searchesByKeywordAndCategory() {
		documents.create(new DocumentSaveRequest("架构设计", null, null, "Spring Boot 分层"), null, "alice");
		documents.create(new DocumentSaveRequest("测试计划", "质量", null, "单元测试"), null, "bob");
		assertThat(documents.list("Spring", null)).hasSize(1);
		assertThat(documents.list("测试", null)).hasSize(1);
		assertThat(documents.list(null, "质量")).hasSize(1);
		assertThat(documents.list("不存在", null)).isEmpty();
	}

	@Test
	void updatesAndCreatesVersionHistoryAndRollsBack() {
		DocumentDetail created = documents.create(
				new DocumentSaveRequest("v1 文档", null, null, "第一版"), null, "alice");
		documents.update(created.id(), new DocumentSaveRequest("v1 文档", null, null, "第二版"), null, "bob");
		documents.createVersion(created.id(), "bob");

		List<DocumentVersionView> versions = documents.versions(created.id());
		assertThat(versions).hasSize(2);
		assertThat(versions.get(0).versionNo()).isEqualTo(2);
		assertThat(versions.get(0).createdBy()).isEqualTo("bob");

		documents.rollback(created.id(), versions.get(1).id(), "alice");
		DocumentDetail rolledBack = documents.get(created.id());
		assertThat(rolledBack.content()).isEqualTo("第一版");
		assertThat(rolledBack.updatedBy()).isEqualTo("alice");
	}

	@Test
	void appendsAndReplaysCollaborationUpdates() {
		DocumentDetail created = documents.create(
				new DocumentSaveRequest("协作文档", null, null, ""), null, "alice");
		documents.appendUpdate(created.id(), new byte[]{1, 2, 3});
		documents.appendUpdate(created.id(), new byte[]{4, 5});
		List<byte[]> replay = documents.replayUpdates(created.id());
		assertThat(replay).hasSize(2);
		assertThat(replay.get(0)).containsExactly(1, 2, 3);

		documents.saveCollaborative(created.id(), "新正文", new byte[]{9}, "bob");
		assertThat(documents.get(created.id()).content()).isEqualTo("新正文");
		// checkpoint 后日志压缩为 1 条(完整状态)
		assertThat(documents.replayUpdates(created.id())).hasSize(1);
	}

	@Test
	void savesAndLoadsAttachment() throws Exception {
		DocumentDetail created = documents.create(
				new DocumentSaveRequest("带附件", null, null, ""), null, "alice");
		MockMultipartFile file = new MockMultipartFile(
				"file", "截图.png", "image/png", "png-bytes".getBytes(StandardCharsets.UTF_8));
		DocumentAttachmentView view = documents.saveAttachment(created.id(), file, "alice");
		assertThat(view.fileName()).isEqualTo("截图.png");
		assertThat(view.url()).startsWith("/api/v1/documents/attachments/");

		StoredDocumentAttachment stored = documents.loadAttachment(view.id());
		assertThat(Files.readString(stored.path())).isEqualTo("png-bytes");
		assertThat(documents.attachments(created.id())).hasSize(1);
	}

	@Test
	void deletesDocumentAndCascades() throws Exception {
		DocumentDetail created = documents.create(
				new DocumentSaveRequest("待删除", null, null, "内容"), null, "alice");
		UUID docId = created.id();
		documents.appendUpdate(docId, new byte[]{1});
		MockMultipartFile file = new MockMultipartFile("file", "a.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
		DocumentAttachmentView attachment = documents.saveAttachment(docId, file, "alice");

		documents.delete(docId);
		assertThat(documents.list(null, null)).isEmpty();
		assertThatThrownBy(() -> documents.get(docId))
				.isInstanceOf(ResponseStatusException.class)
				.hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
		try (var stream = Files.list(storage)) {
			assertThat(stream.findAny()).isEmpty();
		}
	}
}
