package xyz.byqy.vane.iam.application;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamUser;
import xyz.byqy.vane.iam.domain.IamUserRepository;

/** 用户-角色分配（ADM-01）：整体替换，空列表即清空。 */
@Service
public class UserRoleService {

    private final JdbcTemplate jdbc;
    private final IamUserRepository users;

    public UserRoleService(JdbcTemplate jdbc, IamUserRepository users) {
        this.jdbc = jdbc;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<UUID> roleIdsOf(UUID userId) {
        requireUser(userId);
        return jdbc.query("select role_id from iam_user_role where user_id = ? order by role_id",
                (rs, rowNum) -> rs.getObject("role_id", UUID.class), userId);
    }

    @Transactional
    public void replaceRoles(UUID userId, List<UUID> roleIds, UUID actorId) {
        requireUser(userId);
        jdbc.update("delete from iam_user_role where user_id = ?", userId);
        if (roleIds == null) {
            return;
        }
        for (UUID roleId : roleIds.stream().distinct().toList()) {
            Integer exists = jdbc.queryForObject("select count(*) from iam_role where id = ? and deleted_at is null",
                    Integer.class, roleId);
            if (exists == null || exists == 0) {
                throw new ApiException(CommonErrors.REQUEST_NOT_FOUND);
            }
            jdbc.update("insert into iam_user_role (user_id, role_id, created_by) values (?, ?, ?)",
                    userId, roleId, actorId);
        }
    }

    private IamUser requireUser(UUID userId) {
        return users.findById(userId)
                .filter(IamUser::isNotDeleted)
                .orElseThrow(() -> new ApiException(CommonErrors.IAM_USER_NOT_FOUND));
    }
}


