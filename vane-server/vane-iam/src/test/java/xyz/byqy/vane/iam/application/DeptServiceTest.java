package xyz.byqy.vane.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamDept;
import xyz.byqy.vane.iam.domain.IamDeptRepository;
import xyz.byqy.vane.iam.domain.IamUserRepository;

class DeptServiceTest {

    private final IamDeptRepository depts = mock(IamDeptRepository.class);
    private final IamUserRepository users = mock(IamUserRepository.class);
    private final DeptService service = new DeptService(depts, users);

    @Test
    void createRejectsDuplicateCode() {
        when(depts.existsByCodeAndDeletedAtIsNull("HQ")).thenReturn(true);

        assertThatThrownBy(() -> service.create(null, "HQ", "总部", 0))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.IAM_DEPT_CODE_TAKEN);
    }

    @Test
    void deleteRejectsDeptWithChildren() {
        UUID id = UUID.randomUUID();
        IamDept dept = IamDept.create(id, null, "HQ", "总部", 0);
        when(depts.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(dept));
        when(depts.existsByParentIdAndDeletedAtIsNull(id)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.IAM_DEPT_HAS_CHILDREN);
    }

    @Test
    void deleteRejectsDeptWithUsers() {
        UUID id = UUID.randomUUID();
        IamDept dept = IamDept.create(id, null, "HQ", "总部", 0);
        when(depts.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(dept));
        when(depts.existsByParentIdAndDeletedAtIsNull(id)).thenReturn(false);
        when(users.countByDeptIdAndDeletedAtIsNull(id)).thenReturn(2L);

        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.IAM_DEPT_HAS_USERS);
    }

    @Test
    void deletesLeafDeptWithoutMembers() {
        UUID id = UUID.randomUUID();
        IamDept dept = IamDept.create(id, null, "LEAF", "叶子", 0);
        when(depts.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.of(dept));
        when(depts.existsByParentIdAndDeletedAtIsNull(id)).thenReturn(false);
        when(users.countByDeptIdAndDeletedAtIsNull(id)).thenReturn(0L);

        service.delete(id);

        assertThat(dept.isDeleted()).isTrue();
    }

    @Test
    void treeNestsChildrenUnderParent() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        IamDept root = IamDept.create(rootId, null, "HQ", "总部", 0);
        IamDept child = IamDept.create(childId, rootId, "BRANCH", "分部", 10);
        when(depts.findAll(any(Sort.class))).thenReturn(List.of(root, child));

        List<DeptNode> tree = service.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).code()).isEqualTo("HQ");
        assertThat(tree.get(0).children()).hasSize(1);
        assertThat(tree.get(0).children().get(0).code()).isEqualTo("BRANCH");
    }
}
