package com.seeker.share.share;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShareEntryRepository extends JpaRepository<ShareEntry, UUID> {
	List<ShareEntry> findAllByOrderByCreatedAtDesc();
	List<ShareEntry> findAllByExpiresAtBefore(Instant instant);

	@Query("select coalesce(sum(entry.size), 0) from ShareEntry entry")
	long totalSize();
}
