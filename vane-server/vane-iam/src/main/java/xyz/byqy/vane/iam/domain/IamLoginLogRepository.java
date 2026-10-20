package xyz.byqy.vane.iam.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IamLoginLogRepository extends JpaRepository<IamLoginLog, UUID>,
        JpaSpecificationExecutor<IamLoginLog> {
}
