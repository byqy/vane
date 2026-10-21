package xyz.byqy.vane.common.audit;

import java.util.ArrayList;
import java.util.List;

/**
 * 审计变更采集上下文（ADR-023）：由审计切面开启，service 在事务内上报 before/after。
 *
 * <p>放在 common 供各业务模块使用；切面在 audit 模块读取并生成字段级变更。</p>
 */
public final class AuditContext {

    private static final ThreadLocal<AuditContext> CURRENT = new ThreadLocal<>();

    private final List<ChangeRecord> records = new ArrayList<>();
    private Integer affectedCount;

    private AuditContext() {
    }

    /** 切面调用前开启；重复开启会覆盖旧上下文。 */
    public static AuditContext begin() {
        AuditContext context = new AuditContext();
        CURRENT.set(context);
        return context;
    }

    public static AuditContext current() {
        return CURRENT.get();
    }

    /** 切面 finally 调用，避免线程复用串请求。 */
    public static void clear() {
        CURRENT.remove();
    }

    public static void record(Object before, Object after, String... fields) {
        AuditContext context = CURRENT.get();
        if (context != null) {
            context.records.add(new ChangeRecord(before, after, List.of(fields)));
        }
    }

    public static void recordCreated(Object after, String... fields) {
        record(null, after, fields);
    }

    public static void recordDeleted(Object before, String... fields) {
        record(before, null, fields);
    }

    public static void setAffectedCount(int count) {
        AuditContext context = CURRENT.get();
        if (context != null) {
            context.affectedCount = count;
        }
    }

    public Integer affectedCount() {
        return affectedCount;
    }

    public List<ChangeRecord> records() {
        return List.copyOf(records);
    }

    /** 单条变更：before/after 为 Map 或普通对象，字段为白名单。 */
    public record ChangeRecord(Object before, Object after, List<String> fields) {
    }
}
