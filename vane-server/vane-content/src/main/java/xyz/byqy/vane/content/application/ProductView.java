package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 后台产品列表项：含全部语言译文（编辑弹窗直接使用）。 */
public record ProductView(UUID id, String code, String slug, String group, UUID coverFileId, UUID videoFileId,
                          String status, int sortNo, Instant publishedAt, Instant createdAt, Instant updatedAt,
                          List<ProductTranslation> translations) {
}
