package xyz.byqy.vane.iam.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IamUserRepository extends JpaRepository<IamUser, UUID>, JpaSpecificationExecutor<IamUser> {

    Optional<IamUser> findByUsernameAndDeletedAtIsNull(String username);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    long countByDeptIdAndDeletedAtIsNull(UUID deptId);
}



