package com.seeker.share.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentUpdateRepository extends JpaRepository<DocumentUpdateEntity, Long> {

	List<DocumentUpdateEntity> findByDocIdOrderBySeqAsc(UUID docId);

	Optional<DocumentUpdateEntity> findTopByDocIdOrderBySeqDesc(UUID docId);

	void deleteByDocId(UUID docId);
}
