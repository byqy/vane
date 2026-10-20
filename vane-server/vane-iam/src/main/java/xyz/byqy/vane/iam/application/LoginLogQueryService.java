package xyz.byqy.vane.iam.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import xyz.byqy.vane.iam.domain.IamLoginLog;
import xyz.byqy.vane.iam.domain.IamLoginLogRepository;

/** 登录日志查询（ADM-10）：支持用户名、结果与时间范围过滤。 */
@Service
public class LoginLogQueryService {

    private static final int EXPORT_LIMIT = 5000;

    private final IamLoginLogRepository logs;

    public LoginLogQueryService(IamLoginLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public PageResult<LoginLogView> page(String username, String result, Instant from, Instant to,
                                         int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);

        Specification<IamLoginLog> spec = specification(username, result, from, to);

        Page<IamLoginLog> page = logs.findAll(spec,
                PageRequest.of(number, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<LoginLogView> items = page.getContent().stream()
                .map(l -> new LoginLogView(l.getId(), l.getUserId(), l.getUsername(), l.getResult(), l.getDetail(),
                        l.getIp(), l.getTraceId(), l.getCreatedAt()))
                .toList();
        return new PageResult<>(items, new PageResult.PageMeta(number, size, page.getTotalElements()));
    }

    /** 导出：最多 5000 行，按时间倒序。 */
    @Transactional(readOnly = true)
    public List<LoginLogView> exportRows(String username, String result, Instant from, Instant to) {
        return logs.findAll(specification(username, result, from, to),
                        PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .map(l -> new LoginLogView(l.getId(), l.getUserId(), l.getUsername(), l.getResult(), l.getDetail(),
                        l.getIp(), l.getTraceId(), l.getCreatedAt()))
                .toList();
    }

    private Specification<IamLoginLog> specification(String username, String result, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }
            if (result != null && !result.isBlank()) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public record LoginLogView(java.util.UUID id, java.util.UUID userId, String username, String result,
                               String detail, String ip, String traceId, Instant createdAt) {
    }
}

