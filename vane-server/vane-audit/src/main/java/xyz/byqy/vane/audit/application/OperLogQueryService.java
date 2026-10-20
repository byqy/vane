package xyz.byqy.vane.audit.application;

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
import xyz.byqy.vane.audit.domain.SysOperLog;
import xyz.byqy.vane.audit.domain.SysOperLogRepository;

/** 操作日志查询（ADM-10）：支持模块、动作、结果、用户与时间范围过滤，可导出。 */
@Service
public class OperLogQueryService {

    private static final int EXPORT_LIMIT = 5000;

    private final SysOperLogRepository logs;

    public OperLogQueryService(SysOperLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public PageResult<OperLogView> page(String module, String action, String result, String username,
                                        Instant from, Instant to, int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        Page<SysOperLog> page = logs.findAll(specification(module, action, result, username, from, to),
                PageRequest.of(number, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResult<>(page.getContent().stream().map(this::toView).toList(),
                new PageResult.PageMeta(number, size, page.getTotalElements()));
    }

    /** 导出：最多 5000 行，按时间倒序。 */
    @Transactional(readOnly = true)
    public List<OperLogView> exportRows(String module, String action, String result, String username,
                                        Instant from, Instant to) {
        return logs.findAll(specification(module, action, result, username, from, to),
                        PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream().map(this::toView).toList();
    }

    private Specification<SysOperLog> specification(String module, String action, String result,
                                                    String username, Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (module != null && !module.isBlank()) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (result != null && !result.isBlank()) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (username != null && !username.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
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

    private OperLogView toView(SysOperLog log) {
        return new OperLogView(log.getId(), log.getUsername(), log.getModule(), log.getAction(),
                log.getHttpMethod(), log.getPath(), log.getParams(),
                log.getResult(), log.getErrorCode(), log.getDurationMs(), log.getIp(), log.getTraceId(),
                log.getCreatedAt());
    }
}


