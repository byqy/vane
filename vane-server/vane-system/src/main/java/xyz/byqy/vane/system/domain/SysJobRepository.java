package xyz.byqy.vane.system.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SysJobRepository extends JpaRepository<SysJob, UUID> {

    List<SysJob> findByDeletedAtIsNullOrderByJobCodeAsc();

    List<SysJob> findByStatusAndDeletedAtIsNull(String status);

    Optional<SysJob> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByJobCodeAndDeletedAtIsNull(String jobCode);
}
