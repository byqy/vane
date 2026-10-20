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

    /** 动作标识，如 create、update、delete、reset-password。 */
    String action();
}
