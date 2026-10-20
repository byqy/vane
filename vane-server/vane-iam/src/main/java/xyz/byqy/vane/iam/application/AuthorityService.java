package xyz.byqy.vane.iam.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 取用户的权限码，供认证过滤器建立 authorities（ADR-021：不再与权限表关联）。 */
@Service
public class AuthorityService {

    private final RoleAssignmentRepository assignments;

    public AuthorityService(RoleAssignmentRepository assignments) {
        this.assignments = assignments;
    }

    @Transactional(readOnly = true)
    public List<String> codesOf(UUID userId) {
        return assignments.permissionCodesOfUser(userId);
    }
}
