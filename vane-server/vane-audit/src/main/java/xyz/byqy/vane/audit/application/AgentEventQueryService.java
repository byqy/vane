package xyz.byqy.vane.audit.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;

import xyz.byqy.vane.audit.domain.SysOperLog;
import xyz.byqy.vane.audit.domain.SysOperLogRepository;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/**
 * 审计事件的只读查询（ADR-030 P1）。
 *
 * <p>游标为不透明字符串（时间的毫秒值 + 记录 id），按 {@code created_at, id} 升序推进，
 * 避免页码分页在持续写入时漏读或重读。</p>
 */
@Service
public class AgentEventQueryService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final SysOperLogRepository logs;

    public AgentEventQueryService(SysOperLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public AgentEventPage events(Instant since, Instant until, String cursor, Integer limit) {
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        Cursor position = decode(cursor);
        Specification<SysOperLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (since != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), since));
            }
            if (until != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), until));
            }
            if (position != null) {
                predicates.add(cb.or(
                        cb.greaterThan(root.get("createdAt"), position.at()),
                        cb.and(cb.equal(root.get("createdAt"), position.at()),
                                cb.greaterThan(root.get("id"), position.id()))));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<SysOperLog> rows = logs.findAll(spec, PageRequest.of(0, size + 1,
                        Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"))))
                .getContent();
        boolean hasMore = rows.size() > size;
        List<SysOperLog> page = hasMore ? rows.subList(0, size) : rows;
        String after = hasMore ? encode(page.get(page.size() - 1)) : null;
        return new AgentEventPage(page.stream().map(AgentEventQueryService::toEvent).toList(),
                new AgentEventPage.PageMeta(size, after, hasMore));
    }

    private static AgentEvent toEvent(SysOperLog log) {
        return new AgentEvent(
                log.getId().toString(),
                log.getCreatedAt(),
                log.getUsername(),
                log.getModule(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getTargetLabel(),
                log.getHttpMethod(),
                log.getPath(),
                intOrNull(log.getHttpStatus()),
                log.getResult(),
                log.getErrorCode(),
                log.getTraceId());
    }

    private static Integer intOrNull(Short value) {
        return value == null ? null : value.intValue();
    }

    private static String encode(SysOperLog log) {
        // 保留完整精度（PostgreSQL 存到微秒，截断到毫秒会让同一条记录在下一页重复出现）
        String raw = log.getCreatedAt() + "|" + log.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int split = raw.indexOf('|');
            if (split <= 0) {
                throw new IllegalArgumentException("缺少分隔符");
            }
            return new Cursor(Instant.parse(raw.substring(0, split)),
                    UUID.fromString(raw.substring(split + 1)));
        } catch (RuntimeException e) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED, "游标无效，请从上一页响应里取 nextCursor");
        }
    }

    private record Cursor(Instant at, UUID id) {
    }
}
