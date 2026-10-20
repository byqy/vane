package xyz.byqy.vane.iam.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import xyz.byqy.vane.iam.domain.PermissionCatalog;

/** 权限点树（菜单 + 按钮），由代码目录生成，供角色授权界面选择（ADR-021）。 */
@Service
public class PermissionTreeService {

    private static final Comparator<PermissionCatalog> ORDER =
            Comparator.comparingInt(PermissionCatalog::getSortNo).thenComparing(PermissionCatalog::getCode);

    public List<PermissionNode> tree() {
        List<PermissionCatalog> all = PermissionCatalog.all().stream().sorted(ORDER).toList();
        Map<String, List<PermissionNode>> childrenByParent = new LinkedHashMap<>();
        List<PermissionNode> roots = new ArrayList<>();
        for (PermissionCatalog permission : all) {
            PermissionNode node = toNode(permission);
            String parentCode = permission.getParentCode();
            if (parentCode == null) {
                roots.add(node);
            } else {
                childrenByParent.computeIfAbsent(parentCode, key -> new ArrayList<>()).add(node);
            }
        }
        return roots.stream().map(root -> deep(root, childrenByParent)).toList();
    }

    private PermissionNode deep(PermissionNode node, Map<String, List<PermissionNode>> childrenByParent) {
        List<PermissionNode> children = childrenByParent.getOrDefault(node.code(), List.of()).stream()
                .map(child -> deep(child, childrenByParent))
                .toList();
        return new PermissionNode(node.id(), node.code(), node.parentCode(), node.nameKey(), node.type(),
                node.path(), node.component(), node.icon(), node.sortNo(), children);
    }

    private PermissionNode toNode(PermissionCatalog permission) {
        return new PermissionNode(permission.getCode(), permission.getCode(), permission.getParentCode(),
                permission.getNameKey(), permission.getType().name(), permission.getPath(),
                permission.getComponent(), permission.getIcon(), permission.getSortNo(), new ArrayList<>());
    }
}
