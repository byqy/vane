package xyz.byqy.vane.iam.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.iam.domain.IamRole;
import xyz.byqy.vane.iam.domain.IamRoleRepository;

@Service
public class RoleQueryService {

    private final IamRoleRepository roles;

    public RoleQueryService(IamRoleRepository roles) {
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public PageResult<RoleSummary> page(int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        Page<IamRole> page = roles.findAll(PageRequest.of(number, size, Sort.by("sortNo", "code")));
        return new PageResult<>(page.getContent().stream().map(this::toSummary).toList(),
                new PageResult.PageMeta(number, size, page.getTotalElements()));
    }

    RoleSummary toSummary(IamRole role) {
        return new RoleSummary(role.getId(), role.getCode(), role.getName(), role.getDataScope(),
                role.isBuiltin(), role.getStatus(), role.getSortNo());
    }
}
