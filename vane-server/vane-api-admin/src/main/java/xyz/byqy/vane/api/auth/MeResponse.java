package xyz.byqy.vane.api.auth;

import java.util.List;
import java.util.UUID;

/** 当前用户：身份、角色、权限点与可见菜单（前端据此生成动态路由）。 */
public record MeResponse(UUID id, String username, UUID deptId, boolean mustChangePassword, List<String> roles,
                         List<String> permissions, List<MenuItem> menus, String locale, String timezone) {
}



