package xyz.byqy.vane.audit.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface SysOperLogRepository extends JpaRepository<SysOperLog, UUID>, JpaSpecificationExecutor<SysOperLog> {

    /** 响应完成后回填 HTTP 状态（唯一允许的审计行更新）。 */
    @Modifying
    @Transactional
    @Query("update SysOperLog log set log.httpStatus = :status where log.id = :id")
    int updateHttpStatus(@Param("id") UUID id, @Param("status") Short status);
}
