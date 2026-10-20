package xyz.byqy.vane.content.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsBannerRepository extends JpaRepository<CmsBanner, UUID> {

    List<CmsBanner> findByDeletedAtIsNullOrderBySortNoAsc();

    List<CmsBanner> findByPositionAndDeletedAtIsNullOrderBySortNoAsc(String position);

    Optional<CmsBanner> findByIdAndDeletedAtIsNull(UUID id);
}
