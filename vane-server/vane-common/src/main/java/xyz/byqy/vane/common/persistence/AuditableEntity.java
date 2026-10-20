package xyz.byqy.vane.common.persistence;

import java.util.UUID;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.data.annotation.LastModifiedBy;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

/**
 * 审计字段：记录是谁创建/最后修改了这条数据（UUID，对应 iam_user.id）。
 *
 * <p>由 Spring Data JPA Auditing 自动填充，取值来自当前登录用户；
 * 系统任务/启动引导等无登录上下文的写入保持为 null（表示「系统」）。</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private UUID updatedBy;

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}

