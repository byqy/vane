package xyz.byqy.vane.content.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsCategoryRepository extends JpaRepository<CmsCategory, UUID> {

    Optional<CmsCategory> findByIdAndDeletedAtIsNull(UUID id);

    Optional<CmsCategory> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    List<CmsCategory> findAllByDeletedAtIsNullOrderBySortNoAscCodeAsc();

    List<CmsCategory> findAllByStatusAndDeletedAtIsNullOrderBySortNoAscCodeAsc(String status);
}
