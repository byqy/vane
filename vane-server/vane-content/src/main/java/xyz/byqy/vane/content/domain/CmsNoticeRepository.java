package xyz.byqy.vane.content.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CmsNoticeRepository extends JpaRepository<CmsNotice, UUID>, JpaSpecificationExecutor<CmsNotice> {

    Optional<CmsNotice> findByIdAndDeletedAtIsNull(UUID id);

    org.springframework.data.domain.Page<CmsNotice> findByDeletedAtIsNull(org.springframework.data.domain.Pageable pageable);
}
