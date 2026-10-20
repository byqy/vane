package xyz.byqy.vane.iam.application;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.iam.domain.IamDeptRepository;
import xyz.byqy.vane.iam.domain.IamRoleRepository;

/**
 * 解析用户的数据范围（02 §2、D-08）：
 * 多角色取并集，任一角色为 ALL 即不受限；无角色时退化为仅本人。
 */
@Service
public class DataScopeResolver {

    private final IamRoleRepository roles;
    private final IamDeptRepository depts;

    public DataScopeResolver(IamRoleRepository roles, IamDeptRepository depts) {
        this.roles = roles;
        this.depts = depts;
    }

    @Transactional(readOnly = true)
    public DataScopeContext resolve(UUID userId, UUID deptId) {
        List<String> scopes = roles.findDataScopesByUserId(userId);
        if (scopes.isEmpty()) {
            return DataScopeContext.selfOnly(userId);
        }
        if (scopes.contains("ALL")) {
            return DataScopeContext.unrestricted(userId);
        }

        Set<UUID> visible = new HashSet<>();
        boolean selfOnly = false;
        for (String scope : scopes) {
            switch (scope) {
                case "DEPT" -> {
                    if (deptId != null) {
                        visible.add(deptId);
                    }
                }
                case "DEPT_AND_CHILD" -> {
                    if (deptId != null) {
                        visible.addAll(depts.findSubtreeIds(deptId));
                    }
                }
                case "CUSTOM" -> visible.addAll(roles.findCustomDeptIdsByUserId(userId));
                case "SELF" -> selfOnly = true;
                default -> {
                    // 未知范围按最小权限处理
                }
            }
        }
        return new DataScopeContext(false, Set.copyOf(visible), selfOnly, userId);
    }
}
