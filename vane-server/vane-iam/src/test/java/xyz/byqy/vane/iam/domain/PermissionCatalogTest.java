package xyz.byqy.vane.iam.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PermissionCatalogTest {

    @Test
    void catalogHasAllSeededPermissions() {
        assertThat(PermissionCatalog.all()).hasSize(82);
        assertThat(PermissionCatalog.menus())
                .extracting(PermissionCatalog::getCode)
                .contains("system", "system:user", "content:article", "ops:job", "audit:oper-log");
    }

    @Test
    void codesAreUniqueAndWellFormed() {
        List<String> codes = PermissionCatalog.all().stream().map(PermissionCatalog::getCode).toList();
        assertThat(codes).doesNotHaveDuplicates();
        for (String code : codes) {
            assertThat(code)
                    .as("code format: %s", code)
                    .matches("^[a-z][a-z0-9]*(-[a-z0-9]+)*(:[a-z][a-z0-9]*(-[a-z0-9]+)*)*$");
        }
    }

    @Test
    void parentsExistAndAreMenus() {
        for (PermissionCatalog permission : PermissionCatalog.all()) {
            String parentCode = permission.getParentCode();
            if (parentCode == null) {
                assertThat(permission.getType())
                        .as("root must be a menu: %s", permission.getCode())
                        .isEqualTo(PermissionCatalog.Type.MENU);
                continue;
            }
            PermissionCatalog parent = PermissionCatalog.find(parentCode)
                    .orElseThrow(() -> new AssertionError("missing parent " + parentCode + " of " + permission.getCode()));
            assertThat(parent.getType()).as("parent type of %s", permission.getCode())
                    .isEqualTo(PermissionCatalog.Type.MENU);
        }
    }

    @Test
    void menusCarryPathAndButtonsDoNot() {
        for (PermissionCatalog permission : PermissionCatalog.all()) {
            if (permission.getType() == PermissionCatalog.Type.MENU) {
                assertThat(permission.getPath()).as("path of %s", permission.getCode()).isNotBlank();
            } else {
                assertThat(permission.getPath()).as("button path of %s", permission.getCode()).isNull();
            }
        }
    }

    @Test
    void nameKeyIsDerivedFromCode() {
        assertThat(PermissionCatalog.find("system:user").orElseThrow().getNameKey()).isEqualTo("menu.system.user");
        assertThat(PermissionCatalog.find("system:user:create").orElseThrow().getNameKey())
                .isEqualTo("perm.system.user.create");
        assertThat(PermissionCatalog.find("system:user:reset-password").orElseThrow().getNameKey())
                .isEqualTo("perm.system.user.resetPassword");
        assertThat(PermissionCatalog.find("audit:login-log").orElseThrow().getNameKey())
                .isEqualTo("menu.audit.loginLog");
    }

    @Test
    void nameKeysAreUniqueAndBounded() {
        List<String> keys = PermissionCatalog.all().stream().map(PermissionCatalog::getNameKey).toList();
        assertThat(keys).doesNotHaveDuplicates();
        for (String key : keys) {
            assertThat(key).as("nameKey: %s", key).matches("^(menu|perm)\\.[a-z][a-zA-Z0-9]*(\\.[a-zA-Z0-9_]+)*$");
        }
    }

    @Test
    void lookupHelpers() {
        assertThat(PermissionCatalog.exists("system:user")).isTrue();
        assertThat(PermissionCatalog.exists("system:unknown")).isFalse();
        assertThat(PermissionCatalog.find("system:unknown")).isEmpty();
    }
}
