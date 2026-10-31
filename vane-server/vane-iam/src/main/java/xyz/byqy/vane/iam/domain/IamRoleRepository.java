package xyz.byqy.vane.iam.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IamRoleRepository extends JpaRepository<IamRole, UUID> {

    boolean existsByCodeAndDeletedAtIsNull(String code);

    java.util.Optional<IamRole> findByIdAndDeletedAtIsNull(UUID id);

    /** 用户是否持有服务角色（服务账号 / Agent 用途，ADR-030 P1）。 */
    @org.springframework.data.jpa.repository.Query(nativeQuery = true, value = """
            select exists(select 1 from iam_role r
                          join iam_user_role ur on ur.role_id = r.id
                          where ur.user_id = :userId and r.service and r.deleted_at is null)
            """)
    boolean hasServiceRole(@org.springframework.data.repository.query.Param("userId") java.util.UUID userId);

    org.springframework.data.domain.Page<IamRole> findByDeletedAtIsNull(org.springframework.data.domain.Pageable pageable);

    @Query(value = """
            select r.code from iam_role r
            join iam_user_role ur on ur.role_id = r.id
            where ur.user_id = :userId and r.status = 'ACTIVE' and r.deleted_at is null
            order by r.code
            """, nativeQuery = true)
    List<String> findRoleCodesByUserId(@Param("userId") UUID userId);

    @Query(value = """
            select r.data_scope from iam_role r
            join iam_user_role ur on ur.role_id = r.id
            where ur.user_id = :userId and r.status = 'ACTIVE' and r.deleted_at is null
            """, nativeQuery = true)
    List<String> findDataScopesByUserId(@Param("userId") UUID userId);

    @Query(value = """
            select rd.dept_id from iam_role_dept rd
            join iam_user_role ur on ur.role_id = rd.role_id
            where ur.user_id = :userId
            """, nativeQuery = true)
    List<UUID> findCustomDeptIdsByUserId(@Param("userId") UUID userId);
}


