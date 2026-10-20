package xyz.byqy.vane.file.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SysFileRepository extends JpaRepository<SysFile, UUID> {

    Optional<SysFile> findByIdAndDeletedAtIsNull(UUID id);

    Optional<SysFile> findFirstBySha256AndDeletedAtIsNull(String sha256);

    org.springframework.data.domain.Page<SysFile> findByDeletedAtIsNull(org.springframework.data.domain.Pageable pageable);
}

