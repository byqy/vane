package xyz.byqy.vane.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.iam.domain.IamDeptRepository;
import xyz.byqy.vane.iam.domain.IamRoleRepository;

class DataScopeResolverTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID DEPT = UUID.randomUUID();

    private final IamRoleRepository roles = mock(IamRoleRepository.class);
    private final IamDeptRepository depts = mock(IamDeptRepository.class);
    private final DataScopeResolver resolver = new DataScopeResolver(roles, depts);

    @Test
    void allScopeIsUnrestricted() {
        when(roles.findDataScopesByUserId(USER)).thenReturn(List.of("ALL", "SELF"));

        DataScopeContext context = resolver.resolve(USER, DEPT);

        assertThat(context.all()).isTrue();
        assertThat(context.deptIds()).isEmpty();
    }

    @Test
    void deptScopeSeesOwnDept() {
        when(roles.findDataScopesByUserId(USER)).thenReturn(List.of("DEPT"));

        DataScopeContext context = resolver.resolve(USER, DEPT);

        assertThat(context.all()).isFalse();
        assertThat(context.deptIds()).containsExactly(DEPT);
        assertThat(context.selfOnly()).isFalse();
    }

    @Test
    void deptAndChildScopeExpandsSubtree() {
        UUID child = UUID.randomUUID();
        when(roles.findDataScopesByUserId(USER)).thenReturn(List.of("DEPT_AND_CHILD"));
        when(depts.findSubtreeIds(DEPT)).thenReturn(List.of(DEPT, child));

        DataScopeContext context = resolver.resolve(USER, DEPT);

        assertThat(context.deptIds()).containsExactlyInAnyOrder(DEPT, child);
    }

    @Test
    void customScopeUsesRoleDeptRelation() {
        UUID other = UUID.randomUUID();
        when(roles.findDataScopesByUserId(USER)).thenReturn(List.of("CUSTOM"));
        when(roles.findCustomDeptIdsByUserId(USER)).thenReturn(List.of(other));

        DataScopeContext context = resolver.resolve(USER, DEPT);

        assertThat(context.deptIds()).containsExactly(other);
    }

    @Test
    void selfScopeOnlySeesOwnRecords() {
        when(roles.findDataScopesByUserId(USER)).thenReturn(List.of("SELF"));

        DataScopeContext context = resolver.resolve(USER, DEPT);

        assertThat(context.selfOnly()).isTrue();
        assertThat(context.deptIds()).isEmpty();
    }

    @Test
    void userWithoutRoleFallsBackToSelfOnly() {
        when(roles.findDataScopesByUserId(USER)).thenReturn(List.of());

        DataScopeContext context = resolver.resolve(USER, DEPT);

        assertThat(context.all()).isFalse();
        assertThat(context.selfOnly()).isTrue();
    }
}
