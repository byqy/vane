package xyz.byqy.vane.content.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsTagRepository extends JpaRepository<CmsTag, UUID> {

    Optional<CmsTag> findByIdAndDeletedAtIsNull(UUID id);

    Optional<CmsTag> findByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    List<CmsTag> findAllByDeletedAtIsNullOrderByCodeAsc();
}
