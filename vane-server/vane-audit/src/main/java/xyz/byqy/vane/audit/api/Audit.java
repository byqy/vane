package xyz.byqy.vane.audit.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要写入操作日志的方法（06 §9：登录、权限变更、配置变更、导出等）。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {

    /** 模块标识，如 user、role、config。 */
    String module();

    /** 动作标识，如 create、update、delete、reset-password、export。 */
    String action();

    /** 目标类型，如 user、role、config、sms-provider；为空表示不记录目标（L0）。 */
    String targetType() default "";

    /** 目标 ID 的 SpEL 表达式，如 "#id" 或 "#p0"；解析失败只记空。 */
    String targetId() default "";

    /** 目标可读快照的 SpEL 表达式，如 "#request.username"。 */
    String targetLabel() default "";

    /** 允许 service 通过 AuditContext 上报字段级变更（L2）。 */
    boolean detail() default false;
}
