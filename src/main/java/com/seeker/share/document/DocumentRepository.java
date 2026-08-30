package com.seeker.share.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<DocumentEntity, UUID> {

	List<DocumentEntity> findAllByOrderByUpdatedAtDesc();

	@Query("""
			select d from DocumentEntity d
			where (:q is null or lower(d.title) like lower(concat('%', :q, '%'))
				or lower(cast(d.content as string)) like lower(concat('%', :q, '%'))
				or d.tags like concat('%', :q, '%'))
			  and (:category is null or d.category = :category)
			order by d.updatedAt desc""")
	List<DocumentEntity> search(@Param("q") String q, @Param("category") String category);

	@Query("select distinct d.category from DocumentEntity d where d.category is not null order by d.category")
	List<String> findDistinctCategories();

	Optional<DocumentEntity> findById(UUID id);
}
