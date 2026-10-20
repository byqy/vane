package xyz.byqy.vane.iam.application;

import java.util.List;

/** 权限点树节点。节点 id 与 code 相同：权限定义由代码目录决定（ADR-021）。 */
public record PermissionNode(String id, String code, String parentCode, String nameKey, String type,
                             String path, String component, String icon, int sortNo,
                             List<PermissionNode> children) {
}
