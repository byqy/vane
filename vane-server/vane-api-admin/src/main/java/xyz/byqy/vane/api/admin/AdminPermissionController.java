package xyz.byqy.vane.api.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.iam.application.PermissionNode;
import xyz.byqy.vane.iam.application.PermissionTreeService;

/** 权限点树（ADM-05）：菜单与按钮，供角色授权界面使用。 */
@RestController
@RequestMapping("/api/v1/admin/permissions")
public class AdminPermissionController {

    private final PermissionTreeService tree;

    public AdminPermissionController(PermissionTreeService tree) {
        this.tree = tree;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_PERMISSION.code)")
    public List<PermissionNode> tree() {
        return tree.tree();
    }
}
