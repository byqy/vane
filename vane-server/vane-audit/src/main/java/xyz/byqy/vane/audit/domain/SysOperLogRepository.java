package xyz.byqy.vane.audit.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SysOperLogRepository extends JpaRepository<SysOperLog, UUID>, JpaSpecificationExecutor<SysOperLog> {
}
