package xyz.byqy.vane.iam.application;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 角色授权与自定义数据范围的组织关联（关联表物理删除）。 */
public interface RoleAssignmentRepository {

    void replacePermissions(UUID roleId, Collection<String> permissionCodes, UUID actorId);

    void replaceCustomDepts(UUID roleId, Collection<UUID> deptIds, UUID actorId);

    /** 角色已授权的权限码。 */
    List<String> permissionCodesOf(UUID roleId);

    /** 用户通过其角色获得的权限码（去重）。 */
    List<String> permissionCodesOfUser(UUID userId);

    List<UUID> customDeptIdsOf(UUID roleId);
}
