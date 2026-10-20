package xyz.byqy.vane.content.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CmsArticleRepository extends JpaRepository<CmsArticle, UUID>,
        JpaSpecificationExecutor<CmsArticle> {

    Optional<CmsArticle> findByIdAndDeletedAtIsNull(UUID id);

    Optional<CmsArticle> findBySlugAndStatusAndDeletedAtIsNull(String slug, String status);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    boolean existsByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    List<CmsArticle> findAllByStatusAndDeletedAtIsNullOrderByPublishedAtDesc(String status);
}
