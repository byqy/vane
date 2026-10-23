package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 后台产品详情：列表字段 + 媒体集。 */
public record ProductDetailView(UUID id, String code, String slug, String group, UUID coverFileId, UUID videoFileId,
                                String status, int sortNo, Instant publishedAt, Instant createdAt, Instant updatedAt,
                                List<ProductTranslation> translations, List<ProductMediaView> media) {
}
