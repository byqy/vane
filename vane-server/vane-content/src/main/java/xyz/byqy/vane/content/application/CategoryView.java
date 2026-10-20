package xyz.byqy.vane.content.application;

import java.util.List;
import java.util.UUID;

/** 后台分类视图（含全部语言译文）。 */
public record CategoryView(UUID id, UUID parentId, String code, int sortNo, String status,
                           List<CategoryTranslation> translations) {
}
