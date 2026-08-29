package com.seeker.share.share;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShareService {

	private final Map<UUID, ShareItem> items = new ConcurrentHashMap<>();
	private final Path storageLocation;
	private final long storageLimit;
	private final long expirationHours;
	private final ShareEventService events;

	public ShareService(
			@Value("${seeker.share.storage-location}") String storageLocation,
			@Value("${seeker.share.max-storage-bytes}") long storageLimit,
			@Value("${seeker.share.expiration-hours}") long expirationHours,
			ShareEventService events) {
		this.storageLocation = Path.of(storageLocation).toAbsolutePath().normalize();
		this.storageLimit = storageLimit;
		this.expirationHours = expirationHours;
		this.events = events;
	}

	@PostConstruct
	void initializeStorage() throws IOException {
		Files.createDirectories(storageLocation);
	}

	public ShareSnapshot snapshot() {
		List<ShareItem> allItems = items.values().stream()
				.sorted(Comparator.comparing(ShareItem::createdAt).reversed())
				.toList();
		long messages = allItems.stream().filter(item -> item.type() == ShareType.MESSAGE).count();
		long files = allItems.size() - messages;
		long used = allItems.stream().mapToLong(ShareItem::size).sum();
		return new ShareSnapshot(allItems, new ShareStats(allItems.size(), messages, files, used, storageLimit));
	}

	public List<ShareItem> findAll() {
		return snapshot().items();
	}

	public synchronized ShareItem addMessage(String content) {
		Instant now = Instant.now();
		ShareItem item = new ShareItem(
				UUID.randomUUID(), ShareType.MESSAGE, content.strip(), null, null, 0,
				now, now.plus(expirationHours, ChronoUnit.HOURS));
		items.put(item.id(), item);
		events.publishRefresh();
		return item;
	}

	public synchronized ShareItem addFile(MultipartFile file) throws IOException {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要上传的文件");
		}
		long used = items.values().stream().mapToLong(ShareItem::size).sum();
		if (file.getSize() > storageLimit || used + file.getSize() > storageLimit) {
			throw new ResponseStatusException(HttpStatus.INSUFFICIENT_STORAGE, "共享空间不足，请先删除部分文件");
		}

		UUID id = UUID.randomUUID();
		String fileName = safeFileName(file.getOriginalFilename());
		Path target = resolveStoredPath(id);
		try (InputStream inputStream = file.getInputStream()) {
			Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException exception) {
			Files.deleteIfExists(target);
			throw exception;
		}

		Instant now = Instant.now();
		ShareItem item = new ShareItem(
				id, ShareType.FILE, null, fileName, file.getContentType(), file.getSize(),
				now, now.plus(expirationHours, ChronoUnit.HOURS));
		items.put(id, item);
		events.publishRefresh();
		return item;
	}

	public StoredFile findFile(UUID id) {
		ShareItem item = requireItem(id);
		if (item.type() != ShareType.FILE) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在或已被删除");
		}
		Path path = resolveStoredPath(id);
		if (!Files.isRegularFile(path)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文件不存在或已被删除");
		}
		return new StoredFile(item, path);
	}

	public synchronized void delete(UUID id) throws IOException {
		ShareItem item = requireItem(id);
		deleteFile(item);
		items.remove(id);
		events.publishRefresh();
	}

	public synchronized void clear() throws IOException {
		for (ShareItem item : items.values()) {
			deleteFile(item);
		}
		items.clear();
		events.publishRefresh();
	}

	@Scheduled(fixedDelay = 60_000)
	public synchronized void deleteExpired() throws IOException {
		Instant now = Instant.now();
		List<ShareItem> expired = items.values().stream()
				.filter(item -> item.expiresAt().isBefore(now))
				.toList();
		for (ShareItem item : expired) {
			deleteFile(item);
			items.remove(item.id());
		}
		if (!expired.isEmpty()) {
			events.publishRefresh();
		}
	}

	private ShareItem requireItem(UUID id) {
		ShareItem item = items.get(id);
		if (item == null) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "共享内容不存在或已被删除");
		}
		return item;
	}

	private void deleteFile(ShareItem item) throws IOException {
		if (item.type() == ShareType.FILE) {
			Files.deleteIfExists(resolveStoredPath(item.id()));
		}
	}

	private Path resolveStoredPath(UUID id) {
		Path path = storageLocation.resolve(id.toString()).normalize();
		if (!path.startsWith(storageLocation)) {
			throw new IllegalStateException("非法文件路径");
		}
		return path;
	}

	private String safeFileName(String originalFileName) {
		String cleaned = StringUtils.cleanPath(originalFileName == null ? "file" : originalFileName);
		String fileName = Path.of(cleaned).getFileName().toString();
		return fileName.isBlank() ? "file" : fileName;
	}
}
