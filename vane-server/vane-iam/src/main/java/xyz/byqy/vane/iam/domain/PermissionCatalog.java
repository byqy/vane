package xyz.byqy.vane.iam.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * 权限点目录（ADR-021）：权限定义的唯一来源。
 *
 * <p>数据库只保存角色与权限码的授权关系（iam_role_permission.permission_code），
 * 不再保存权限点本身；菜单、授权树、鉴权与界面文案键都从本目录派生。</p>
 */
public enum PermissionCatalog {

    AUDIT_LOGIN_LOG_EXPORT("audit:login-log:export", Type.BUTTON, "audit:login-log", null, null, null, 10, true),
    AUDIT_OPER_LOG("audit:oper-log", Type.MENU, "audit", "/audit/oper-log", "audit/operlog/index", "list", 10, true),
    AUDIT_OPER_LOG_EXPORT("audit:oper-log:export", Type.BUTTON, "audit:oper-log", null, null, null, 10, true),
    CONTENT_ARTICLE_CREATE("content:article:create", Type.BUTTON, "content:article", null, null, null, 10, true),
    CONTENT_BANNER_CREATE("content:banner:create", Type.BUTTON, "content:banner", null, null, null, 10, true),
    CONTENT_CATEGORY_CREATE("content:category:create", Type.BUTTON, "content:category", null, null, null, 10, true),
    CONTENT_NAV_CREATE("content:nav:create", Type.BUTTON, "content:nav", null, null, null, 10, true),
    CONTENT_NOTICE("content:notice", Type.MENU, "content", "/content/notice", "content/notice/index", "bell", 10, true),
    CONTENT_NOTICE_CREATE("content:notice:create", Type.BUTTON, "content:notice", null, null, null, 10, true),
    CONTENT_PAGE_CREATE("content:page:create", Type.BUTTON, "content:page", null, null, null, 10, true),
    CONTENT_TAG_CREATE("content:tag:create", Type.BUTTON, "content:tag", null, null, null, 10, true),
    OPS_FILE_UPLOAD("ops:file:upload", Type.BUTTON, "ops:file", null, null, null, 10, true),
    OPS_JOB("ops:job", Type.MENU, "ops", "/ops/job", "ops/job/index", "clock", 10, true),
    OPS_JOB_CREATE("ops:job:create", Type.BUTTON, "ops:job", null, null, null, 10, true),
    OPS_MAIL_SEND("ops:mail:send", Type.BUTTON, "ops:mail", null, null, null, 10, true),
    SYSTEM_CONFIG_UPDATE("system:config:update", Type.BUTTON, "system:config", null, null, null, 10, true),
    SYSTEM_DEPT_CREATE("system:dept:create", Type.BUTTON, "system:dept", null, null, null, 10, true),
    SYSTEM_DICT_CREATE("system:dict:create", Type.BUTTON, "system:dict", null, null, null, 10, true),
    SYSTEM_PERMISSION_CREATE("system:permission:create", Type.BUTTON, "system:permission", null, null, null, 10, true),
    SYSTEM_ROLE_CREATE("system:role:create", Type.BUTTON, "system:role", null, null, null, 10, true),
    SYSTEM_USER("system:user", Type.MENU, "system", "/system/user", "system/user/index", "user", 10, true),
    SYSTEM_USER_CREATE("system:user:create", Type.BUTTON, "system:user", null, null, null, 10, true),
    AUDIT_LOGIN_LOG("audit:login-log", Type.MENU, "audit", "/audit/login-log", "audit/loginlog/index", "login", 20, true),
    CONTENT_ARTICLE_UPDATE("content:article:update", Type.BUTTON, "content:article", null, null, null, 20, true),
    CONTENT_BANNER_UPDATE("content:banner:update", Type.BUTTON, "content:banner", null, null, null, 20, true),
    CONTENT_CATEGORY_UPDATE("content:category:update", Type.BUTTON, "content:category", null, null, null, 20, true),
    CONTENT_NAV_UPDATE("content:nav:update", Type.BUTTON, "content:nav", null, null, null, 20, true),
    CONTENT_NOTICE_UPDATE("content:notice:update", Type.BUTTON, "content:notice", null, null, null, 20, true),
    CONTENT_PAGE("content:page", Type.MENU, "content", "/content/page", "content/page/index", "document", 20, true),
    CONTENT_PAGE_UPDATE("content:page:update", Type.BUTTON, "content:page", null, null, null, 20, true),
    CONTENT_TAG_DELETE("content:tag:delete", Type.BUTTON, "content:tag", null, null, null, 20, true),
    OPS_FILE("ops:file", Type.MENU, "ops", "/ops/file", "ops/file/index", "folder", 20, true),
    OPS_FILE_DELETE("ops:file:delete", Type.BUTTON, "ops:file", null, null, null, 20, true),
    OPS_JOB_UPDATE("ops:job:update", Type.BUTTON, "ops:job", null, null, null, 20, true),
    SYSTEM_DEPT_UPDATE("system:dept:update", Type.BUTTON, "system:dept", null, null, null, 20, true),
    SYSTEM_DICT_UPDATE("system:dict:update", Type.BUTTON, "system:dict", null, null, null, 20, true),
    SYSTEM_PERMISSION_UPDATE("system:permission:update", Type.BUTTON, "system:permission", null, null, null, 20, true),
    SYSTEM_ROLE("system:role", Type.MENU, "system", "/system/role", "system/role/index", "team", 20, true),
    SYSTEM_ROLE_UPDATE("system:role:update", Type.BUTTON, "system:role", null, null, null, 20, true),
    SYSTEM_USER_UPDATE("system:user:update", Type.BUTTON, "system:user", null, null, null, 20, true),
    CONTENT_ARTICLE_DELETE("content:article:delete", Type.BUTTON, "content:article", null, null, null, 30, true),
    CONTENT_BANNER_DELETE("content:banner:delete", Type.BUTTON, "content:banner", null, null, null, 30, true),
    CONTENT_CATEGORY_DELETE("content:category:delete", Type.BUTTON, "content:category", null, null, null, 30, true),
    CONTENT_NAV("content:nav", Type.MENU, "content", "/content/nav", "content/nav/index", "menu", 30, true),
    CONTENT_NAV_DELETE("content:nav:delete", Type.BUTTON, "content:nav", null, null, null, 30, true),
    CONTENT_NOTICE_DELETE("content:notice:delete", Type.BUTTON, "content:notice", null, null, null, 30, true),
    CONTENT_PAGE_DELETE("content:page:delete", Type.BUTTON, "content:page", null, null, null, 30, true),
    OPS_FILE_DOWNLOAD("ops:file:download", Type.BUTTON, "ops:file", null, null, null, 30, true),
    OPS_HEALTH("ops:health", Type.MENU, "ops", "/ops/health", "ops/health/index", "heart", 30, true),
    OPS_JOB_DELETE("ops:job:delete", Type.BUTTON, "ops:job", null, null, null, 30, true),
    SYSTEM_DEPT("system:dept", Type.MENU, "system", "/system/dept", "system/dept/index", "apartment", 30, true),
    SYSTEM_DEPT_DELETE("system:dept:delete", Type.BUTTON, "system:dept", null, null, null, 30, true),
    SYSTEM_DICT_DELETE("system:dict:delete", Type.BUTTON, "system:dict", null, null, null, 30, true),
    SYSTEM_PERMISSION_DELETE("system:permission:delete", Type.BUTTON, "system:permission", null, null, null, 30, true),
    SYSTEM_ROLE_DELETE("system:role:delete", Type.BUTTON, "system:role", null, null, null, 30, true),
    SYSTEM_USER_DELETE("system:user:delete", Type.BUTTON, "system:user", null, null, null, 30, true),
    CONTENT_ARTICLE_PUBLISH("content:article:publish", Type.BUTTON, "content:article", null, null, null, 40, true),
    CONTENT_BANNER("content:banner", Type.MENU, "content", "/content/banner", "content/banner/index", "picture", 40, true),
    CONTENT_NOTICE_PUBLISH("content:notice:publish", Type.BUTTON, "content:notice", null, null, null, 40, true),
    CONTENT_PAGE_PUBLISH("content:page:publish", Type.BUTTON, "content:page", null, null, null, 40, true),
    OPS_JOB_RUN("ops:job:run", Type.BUTTON, "ops:job", null, null, null, 40, true),
    OPS_MAIL("ops:mail", Type.MENU, "ops", "/ops/mail", "ops/mail/index", "mail", 40, true),
    SYSTEM_PERMISSION("system:permission", Type.MENU, "system", "/system/permission", "system/permission/index", "key", 40, true),
    SYSTEM_ROLE_ASSIGN("system:role:assign", Type.BUTTON, "system:role", null, null, null, 40, true),
    SYSTEM_USER_RESET_PASSWORD("system:user:reset-password", Type.BUTTON, "system:user", null, null, null, 40, true),
    CONTENT_ARTICLE("content:article", Type.MENU, "content", "/content/article", "content/article/index", "file-text", 50, true),
    SYSTEM_CONFIG("system:config", Type.MENU, "system", "/system/config", "system/config/index", "setting", 50, true),
    SYSTEM_USER_EXPORT("system:user:export", Type.BUTTON, "system:user", null, null, null, 50, true),
    CONTENT_CATEGORY("content:category", Type.MENU, "content", "/content/category", "content/category/index", "folder", 60, true),
    SYSTEM_DICT("system:dict", Type.MENU, "system", "/system/dict", "system/dict/index", "book", 60, true),
    CONTENT_TAG("content:tag", Type.MENU, "content", "/content/tag", "content/tag/index", "tag", 70, true),
    SYSTEM("system", Type.MENU, null, "/system", "layout", "setting", 100, true),
    CONTENT("content", Type.MENU, null, "/content", "layout", "document", 200, true),
    OPS("ops", Type.MENU, null, "/ops", "layout", "monitor", 300, true),
    AUDIT("audit", Type.MENU, null, "/audit", "layout", "file-text", 400, true);

    /** 权限点类型：菜单参与路由与菜单下发，按钮仅参与鉴权。 */
    public enum Type {
        MENU,
        BUTTON
    }

    private final String code;
    private final Type type;
    private final String parentCode;
    private final String path;
    private final String component;
    private final String icon;
    private final int sortNo;
    private final boolean visible;

    PermissionCatalog(String code, Type type, String parentCode, String path, String component, String icon,
            int sortNo, boolean visible) {
        this.code = code;
        this.type = type;
        this.parentCode = parentCode;
        this.path = path;
        this.component = component;
        this.icon = icon;
        this.sortNo = sortNo;
        this.visible = visible;
    }

    public String getCode() {
        return code;
    }

    public Type getType() {
        return type;
    }

    public String getParentCode() {
        return parentCode;
    }

    public String getPath() {
        return path;
    }

    public String getComponent() {
        return component;
    }

    public String getIcon() {
        return icon;
    }

    public int getSortNo() {
        return sortNo;
    }

    public boolean isVisible() {
        return visible;
    }

    /** 界面文案键：MENU 为 menu.*，BUTTON 为 perm.*，由 code 派生（ADR-021）。 */
    public String getNameKey() {
        StringBuilder key = new StringBuilder(type == Type.MENU ? "menu." : "perm.");
        String[] segments = code.split(":");
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) {
                key.append('.');
            }
            key.append(toCamel(segments[i]));
        }
        return key.toString();
    }

    private static String toCamel(String segment) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : segment.toCharArray()) {
            if (character == '-') {
                upper = true;
                continue;
            }
            result.append(upper ? Character.toUpperCase(character) : character);
            upper = false;
        }
        return result.toString();
    }

    public static Optional<PermissionCatalog> find(String code) {
        return Arrays.stream(values()).filter(permission -> permission.code.equals(code)).findFirst();
    }

    public static boolean exists(String code) {
        return find(code).isPresent();
    }

    public static List<PermissionCatalog> all() {
        return List.of(values());
    }

    public static List<PermissionCatalog> menus() {
        return Arrays.stream(values()).filter(permission -> permission.type == Type.MENU).toList();
    }
}
