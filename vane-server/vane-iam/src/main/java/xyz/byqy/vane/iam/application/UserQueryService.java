package xyz.byqy.vane.iam.application;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.iam.domain.IamUser;
import xyz.byqy.vane.iam.domain.IamUserRepository;

/**
 * 用户查询：分页上限 100，数据范围在查询层统一注入（D-08）。
 * 受控表：iam_user，归属列 dept_id（05 §7）。
 */
@Service
public class UserQueryService {

    private final IamUserRepository users;
    private final DataScopeResolver dataScopeResolver;
    private final UserSummaryAssembler assembler;

    public UserQueryService(IamUserRepository users, DataScopeResolver dataScopeResolver,
                            UserSummaryAssembler assembler) {
        this.users = users;
        this.dataScopeResolver = dataScopeResolver;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public PageResult<UserSummary> page(UUID actorId, UUID actorDeptId, String username, String status,
                                        int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);

        DataScopeContext scope = dataScopeResolver.resolve(actorId, actorDeptId);
        Specification<IamUser> spec = specification(scope, username, status);

        Page<IamUser> page = users.findAll(spec, PageRequest.of(number, size, Sort.by("username")));
        List<UserSummary> items = assembler.assemble(page.getContent());
        return new PageResult<>(items, new PageResult.PageMeta(number, size, page.getTotalElements()));
    }

    /** 导出：最多 5000 行，数据范围同样生效。 */
    @Transactional(readOnly = true)
    public List<UserSummary> exportRows(UUID actorId, UUID actorDeptId, String username, String status) {
        DataScopeContext scope = dataScopeResolver.resolve(actorId, actorDeptId);
        Specification<IamUser> spec = specification(scope, username, status);
        return assembler.assemble(users.findAll(spec, PageRequest.of(0, 5000, Sort.by("username"))).getContent());
    }

    private Specification<IamUser> specification(DataScopeContext scope, String username, String status) {
        Specification<IamUser> spec = DataScopeSpecification.of(scope)
                .and((root, query, cb) -> cb.isNull(root.get("deletedAt")));
        if (username != null && !username.isBlank()) {
            String keyword = "%" + username.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("username")), keyword));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        return spec;
    }
}


