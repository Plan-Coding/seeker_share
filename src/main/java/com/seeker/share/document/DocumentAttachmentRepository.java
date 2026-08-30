package com.seeker.share.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentAttachmentRepository extends JpaRepository<DocumentAttachmentEntity, UUID> {

	List<DocumentAttachmentEntity> findByDocIdOrderByCreatedAtDesc(UUID docId);

	Optional<DocumentAttachmentEntity> findByStoredName(String storedName);

	void deleteByDocId(UUID docId);
}
