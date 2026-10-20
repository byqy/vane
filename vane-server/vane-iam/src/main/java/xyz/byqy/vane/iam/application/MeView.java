package xyz.byqy.vane.iam.application;

import java.util.List;
import java.util.UUID;

public record MeView(UUID id, String username, UUID deptId, boolean mustChangePassword, List<String> roles,
                     List<String> permissions, List<MenuView> menus, String locale, String timezone) {

    public record MenuView(String code, String parentCode, String nameKey, String path,
                           String component, String icon, int sortNo) {
    }
}
