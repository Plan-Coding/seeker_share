package com.seeker.share.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersionEntity, UUID> {

	List<DocumentVersionEntity> findByDocIdOrderByVersionNoDesc(UUID docId);

	Optional<DocumentVersionEntity> findTopByDocIdOrderByVersionNoDesc(UUID docId);

	void deleteByDocId(UUID docId);
}
