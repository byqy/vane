package xyz.byqy.vane.iam.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.Predicate;
import xyz.byqy.vane.iam.domain.IamUser;

/** 把数据范围转换为查询条件；受控实体必须有 deptId 字段。 */
public final class DataScopeSpecification {

    private DataScopeSpecification() {
    }

    public static Specification<IamUser> of(DataScopeContext context) {
        return (root, query, cb) -> {
            if (context.all()) {
                return cb.conjunction();
            }
            List<Predicate> predicates = new ArrayList<>();
            if (!context.deptIds().isEmpty()) {
                predicates.add(root.get("deptId").in(context.deptIds()));
            }
            if (context.selfOnly()) {
                predicates.add(cb.equal(root.get("id"), context.userId()));
            }
            if (predicates.isEmpty()) {
                return cb.disjunction();
            }
            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }
}
