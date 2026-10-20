package xyz.byqy.vane.api.auth;

public record MenuItem(String code, String parentCode, String nameKey, String path,
                       String component, String icon, int sortNo) {
}
