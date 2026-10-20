package xyz.byqy.vane.content.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsNavRepository extends JpaRepository<CmsNav, UUID> {

    List<CmsNav> findByPositionAndDeletedAtIsNullOrderBySortNoAsc(String position);

    List<CmsNav> findByDeletedAtIsNullOrderBySortNoAsc();

    Optional<CmsNav> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByParentIdAndDeletedAtIsNull(UUID parentId);
}
