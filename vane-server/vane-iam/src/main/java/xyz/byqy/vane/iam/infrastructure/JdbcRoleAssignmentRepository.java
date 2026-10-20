package xyz.byqy.vane.iam.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import xyz.byqy.vane.iam.application.RoleAssignmentRepository;

@Repository
public class JdbcRoleAssignmentRepository implements RoleAssignmentRepository {

    private final JdbcTemplate jdbc;

    public JdbcRoleAssignmentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void replacePermissions(UUID roleId, Collection<String> permissionCodes, UUID actorId) {
        jdbc.update("delete from iam_role_permission where role_id = ?", roleId);
        for (String permissionCode : permissionCodes) {
            jdbc.update("insert into iam_role_permission (role_id, permission_code, created_by) values (?, ?, ?)",
                    roleId, permissionCode, actorId);
        }
    }

    @Override
    public void replaceCustomDepts(UUID roleId, Collection<UUID> deptIds, UUID actorId) {
        jdbc.update("delete from iam_role_dept where role_id = ?", roleId);
        for (UUID deptId : deptIds) {
            jdbc.update("insert into iam_role_dept (role_id, dept_id, created_by) values (?, ?, ?)",
                    roleId, deptId, actorId);
        }
    }

    @Override
    public List<String> permissionCodesOf(UUID roleId) {
        return jdbc.queryForList(
                "select permission_code from iam_role_permission where role_id = ? order by permission_code",
                String.class, roleId);
    }

    @Override
    public List<String> permissionCodesOfUser(UUID userId) {
        return jdbc.queryForList("""
                select distinct rp.permission_code
                  from iam_role_permission rp
                  join iam_user_role ur on ur.role_id = rp.role_id
                 where ur.user_id = ?
                 order by rp.permission_code
                """, String.class, userId);
    }

    @Override
    public List<UUID> customDeptIdsOf(UUID roleId) {
        return jdbc.queryForList("select dept_id from iam_role_dept where role_id = ?", UUID.class, roleId);
    }
}
