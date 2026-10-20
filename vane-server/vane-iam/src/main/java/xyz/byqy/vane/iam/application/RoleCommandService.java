package xyz.byqy.vane.iam.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamRole;
import xyz.byqy.vane.iam.domain.IamRoleRepository;
import xyz.byqy.vane.iam.domain.PermissionCatalog;

/** 角色写操作（ADM-02）：内置角色不可删除，编码创建后不可变。 */
@Service
public class RoleCommandService {

    private final IamRoleRepository roles;
    private final RoleAssignmentRepository assignments;
    private final RoleQueryService queries;

    public RoleCommandService(IamRoleRepository roles, RoleAssignmentRepository assignments,
                              RoleQueryService queries) {
        this.roles = roles;
        this.assignments = assignments;
        this.queries = queries;
    }

    @Transactional
    public RoleSummary create(String code, String name, String dataScope) {
        if (roles.existsByCodeAndDeletedAtIsNull(code)) {
            throw new ApiException(CommonErrors.IAM_ROLE_CODE_TAKEN);
        }
        IamRole role = IamRole.create(UUID.randomUUID(), code, name, dataScope);
        roles.save(role);
        return queries.toSummary(role);
    }

    @Transactional
    public RoleSummary update(UUID id, String name, String dataScope, String status, int sortNo) {
        IamRole role = requireRole(id);
        role.update(name, dataScope, status, sortNo);
        roles.save(role);
        return queries.toSummary(role);
    }

    @Transactional
    public void delete(UUID id) {
        IamRole role = requireRole(id);
        if (role.isBuiltin()) {
            throw new ApiException(CommonErrors.IAM_ROLE_BUILTIN_IMMUTABLE);
        }
        role.softDelete();
        roles.save(role);
        assignments.replacePermissions(id, List.of(), null);
        assignments.replaceCustomDepts(id, List.of(), null);
    }

    @Transactional
    public void assignPermissions(UUID id, Collection<String> permissionCodes, UUID actorId) {
        requireRole(id);
        Set<String> unique = new HashSet<>(permissionCodes);
        boolean unknown = unique.stream().anyMatch(code -> !PermissionCatalog.exists(code));
        if (unknown) {
            throw new ApiException(CommonErrors.IAM_PERMISSION_NOT_FOUND);
        }
        assignments.replacePermissions(id, unique, actorId);
    }

    @Transactional
    public void assignCustomDepts(UUID id, Collection<UUID> deptIds, UUID actorId) {
        IamRole role = requireRole(id);
        if (!"CUSTOM".equals(role.getDataScope())) {
            throw new ApiException(CommonErrors.REQUEST_PRECONDITION_FAILED);
        }
        assignments.replaceCustomDepts(id, new HashSet<>(deptIds), actorId);
    }

    @Transactional(readOnly = true)
    public List<String> permissionCodes(UUID id) {
        requireRole(id);
        return assignments.permissionCodesOf(id);
    }

    @Transactional(readOnly = true)
    public List<UUID> customDeptIds(UUID id) {
        requireRole(id);
        return assignments.customDeptIdsOf(id);
    }

    private IamRole requireRole(UUID id) {
        return roles.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.IAM_ROLE_NOT_FOUND));
    }
}
