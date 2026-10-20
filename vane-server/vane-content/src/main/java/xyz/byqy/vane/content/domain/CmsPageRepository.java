package xyz.byqy.vane.content.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CmsPageRepository extends JpaRepository<CmsPage, UUID>, JpaSpecificationExecutor<CmsPage> {

    Optional<CmsPage> findByIdAndDeletedAtIsNull(UUID id);

    Optional<CmsPage> findBySlugAndStatusAndDeletedAtIsNull(String slug, String status);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    List<CmsPage> findAllByStatusAndDeletedAtIsNullOrderByPublishedAtDesc(String status);
}


