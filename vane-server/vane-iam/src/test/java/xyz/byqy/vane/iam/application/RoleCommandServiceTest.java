package xyz.byqy.vane.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamRole;
import xyz.byqy.vane.iam.domain.IamRoleRepository;

class RoleCommandServiceTest {

    private final IamRoleRepository roles = mock(IamRoleRepository.class);
    private final RoleAssignmentRepository assignments = mock(RoleAssignmentRepository.class);
    private final RoleQueryService queries = mock(RoleQueryService.class);
    private final RoleCommandService service = new RoleCommandService(roles, assignments, queries);

    @Test
    void createRejectsDuplicateCode() {
        when(roles.existsByCodeAndDeletedAtIsNull("DEPT_MGR")).thenReturn(true);

        assertThatThrownBy(() -> service.create("DEPT_MGR", "部门经理", "DEPT", false))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.IAM_ROLE_CODE_TAKEN);
    }

    @Test
    void softDeletesRoleAndClearsAssignments() {
        IamRole role = IamRole.create(UUID.randomUUID(), "DEPT_MGR", "部门经理", "DEPT");
        when(roles.findByIdAndDeletedAtIsNull(role.getId())).thenReturn(Optional.of(role));

        service.delete(role.getId());

        assertThat(role.isDeleted()).isTrue();
        verify(assignments).replacePermissions(role.getId(), List.of(), null);
        verify(assignments).replaceCustomDepts(role.getId(), List.of(), null);
    }

    @Test
    void assignPermissionsRejectsUnknownCode() {
        IamRole role = IamRole.create(UUID.randomUUID(), "DEPT_MGR", "部门经理", "DEPT");
        when(roles.findByIdAndDeletedAtIsNull(role.getId())).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.assignPermissions(role.getId(), List.of("system:not-exist"), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.IAM_PERMISSION_NOT_FOUND);
        verify(assignments, never()).replacePermissions(any(), any(), any());
    }

    @Test
    void assignPermissionsAcceptsCatalogCodes() {
        IamRole role = IamRole.create(UUID.randomUUID(), "DEPT_MGR", "部门经理", "DEPT");
        when(roles.findByIdAndDeletedAtIsNull(role.getId())).thenReturn(Optional.of(role));

        service.assignPermissions(role.getId(), List.of("system:user", "system:user:create"), UUID.randomUUID());

        verify(assignments).replacePermissions(org.mockito.ArgumentMatchers.eq(role.getId()),
                org.mockito.ArgumentMatchers.eq(java.util.Set.of("system:user", "system:user:create")),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void customDeptsRequireCustomDataScope() {
        IamRole role = IamRole.create(UUID.randomUUID(), "DEPT_MGR", "部门经理", "DEPT");
        when(roles.findByIdAndDeletedAtIsNull(role.getId())).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.assignCustomDepts(role.getId(), List.of(UUID.randomUUID()), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.REQUEST_PRECONDITION_FAILED);

        verify(assignments, never()).replaceCustomDepts(any(), any(), any());
    }

    @Test
    void missingRoleBecomesNotFound() {
        UUID id = UUID.randomUUID();
        when(roles.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(id, "x", null, null, 0, null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.IAM_ROLE_NOT_FOUND);
    }
}
