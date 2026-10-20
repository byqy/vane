package xyz.byqy.vane.content.application;

import java.util.List;
import java.util.UUID;

/** 后台分类树节点；业务上只有一级，children 目前恒为空列表。 */
public record CategoryNodeView(UUID id, UUID parentId, String code, int sortNo, String status,
                               List<CategoryTranslation> translations, List<CategoryNodeView> children) {
}
