package xyz.byqy.vane.iam.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IamDeptRepository extends JpaRepository<IamDept, UUID> {

    /** 组织及其全部下级（递归 CTE）。 */
    @Query(value = """
            with recursive subtree as (
                select id from iam_dept where id = :deptId and deleted_at is null
                union all
                select c.id from iam_dept c join subtree s on c.parent_id = s.id where c.deleted_at is null
            )
            select id from subtree
            """, nativeQuery = true)
    List<UUID> findSubtreeIds(@Param("deptId") UUID deptId);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByParentIdAndDeletedAtIsNull(UUID parentId);

    java.util.Optional<IamDept> findByIdAndDeletedAtIsNull(UUID id);

    java.util.List<IamDept> findByDeletedAtIsNullOrderBySortNoAscCodeAsc();
}

