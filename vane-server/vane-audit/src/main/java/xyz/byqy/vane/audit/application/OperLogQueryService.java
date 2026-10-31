package xyz.byqy.vane.audit.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import xyz.byqy.vane.audit.domain.SysOperLog;
import xyz.byqy.vane.audit.domain.SysOperLogRepository;
import xyz.byqy.vane.common.web.CursorToken;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/** 操作日志查询（ADM-10）：目标/来源/结果/用户/时间过滤；列表与详情分开。 */
@Service
public class OperLogQueryService {

    private static final int EXPORT_LIMIT = 5000;

    private final SysOperLogRepository logs;

    public OperLogQueryService(SysOperLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public PageResult<OperLogSummary> page(String module, String action, String result, String username,
                                           String source, String targetType, String targetId, String traceId,
                                           String ip, Integer httpStatus, Instant from, Instant to,
                                           int pageNumber, int pageSize, String after) {
        if (targetId != null && !targetId.isBlank() && (targetType == null || targetType.isBlank())) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        CursorToken.Key cursor = CursorToken.decode(after);
        Specification<SysOperLog> base = specification(module, action, result, username, source, targetType,
                targetId, traceId, ip, httpStatus, from, to);
        Specification<SysOperLog> spec = base;
        if (cursor != null) {
            // 倒序游标：时间更早，或同一时刻主键更小
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.lessThan(root.get("createdAt"), cursor.at()),
                    cb.and(cb.equal(root.get("createdAt"), cursor.at()), cb.lessThan(root.get("id"), cursor.id()))));
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"));
        // 多取一条用于判断是否还有下一页（页码模式与游标模式都要有 nextAfter）
        int offset = cursor == null ? number * size : 0;
        List<SysOperLog> rows = logs.findAll(spec, PageRequest.of(offset, size + 1, sort)).getContent();
        boolean hasMore = rows.size() > size;
        List<SysOperLog> items = hasMore ? rows.subList(0, size) : rows;
        String nextAfter = hasMore && !items.isEmpty()
                ? CursorToken.encode(items.get(items.size() - 1).getCreatedAt(), items.get(items.size() - 1).getId())
                : null;
        long total = logs.count(base);
        return new PageResult<>(items.stream().map(this::toSummary).toList(),
                new PageResult.PageMeta(number, size, total, nextAfter, hasMore));
    }

    @Transactional(readOnly = true)
    public OperLogDetail detail(UUID id) {
        return logs.findById(id).map(this::toDetail)
                .orElseThrow(() -> new ApiException(CommonErrors.REQUEST_NOT_FOUND));
    }

    /** 导出：最多 5000 行，按时间倒序，含参数与变更。 */
    @Transactional(readOnly = true)
    public List<OperLogDetail> exportRows(String module, String action, String result, String username,
                                          String source, String targetType, String targetId, String traceId,
                                          String ip, Integer httpStatus, Instant from, Instant to) {
        return logs.findAll(
                        specification(module, action, result, username, source, targetType, targetId, traceId, ip,
                                httpStatus, from, to),
                        PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream().map(this::toDetail).toList();
    }

    private Specification<SysOperLog> specification(String module, String action, String result, String username,
                                                    String source, String targetType, String targetId,
                                                    String traceId, String ip, Integer httpStatus,
                                                    Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (notBlank(module)) {
                predicates.add(cb.equal(root.get("module"), module));
            }
            if (notBlank(action)) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (notBlank(result)) {
                predicates.add(cb.equal(root.get("result"), result));
            }
            if (notBlank(username)) {
                predicates.add(cb.like(cb.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }
            if (notBlank(source)) {
                predicates.add(cb.equal(root.get("source"), source));
            }
            if (notBlank(targetType)) {
                predicates.add(cb.equal(root.get("targetType"), targetType));
            }
            if (notBlank(targetId)) {
                predicates.add(cb.equal(root.get("targetId"), targetId));
            }
            if (notBlank(traceId)) {
                predicates.add(cb.equal(root.get("traceId"), traceId));
            }
            if (notBlank(ip)) {
                predicates.add(cb.equal(root.get("ip"), ip));
            }
            if (httpStatus != null) {
                predicates.add(cb.equal(root.get("httpStatus"), httpStatus.shortValue()));
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

    private static Integer intOrNull(Short value) {
        return value == null ? null : value.intValue();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private OperLogSummary toSummary(SysOperLog log) {
        return new OperLogSummary(log.getId(), log.getUsername(), log.getModule(), log.getAction(),
                log.getSource(), log.getTargetType(), log.getTargetId(), log.getTargetLabel(),
                log.getHttpMethod(), log.getPath(), intOrNull(log.getHttpStatus()), log.getResult(), log.getErrorCode(),
                log.getDurationMs(), log.getAffectedCount(), log.getIp(), log.getUserAgent(), log.getDeptId(),
                log.getTraceId(), log.getCreatedAt());
    }

    private OperLogDetail toDetail(SysOperLog log) {
        return new OperLogDetail(log.getId(), log.getUsername(), log.getModule(), log.getAction(),
                log.getSource(), log.getTargetType(), log.getTargetId(), log.getTargetLabel(),
                log.getHttpMethod(), log.getPath(), intOrNull(log.getHttpStatus()), log.getResult(), log.getErrorCode(),
                log.getDurationMs(), log.getAffectedCount(), log.getIp(), log.getUserAgent(), log.getDeptId(),
                log.getTraceId(), log.getCreatedAt(), log.getParams(), log.getChanges(),
                log.getChangesVersion(), log.isChangesTruncated());
    }
}
