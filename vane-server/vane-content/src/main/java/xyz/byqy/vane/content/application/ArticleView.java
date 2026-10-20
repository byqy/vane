package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 后台文章视图（含全部语言译文与标签）。 */
public record ArticleView(UUID id, String slug, String status, UUID categoryId, UUID coverFileId,
                          Instant publishedAt, List<TagView> tags, List<ArticleTranslation> translations) {
}
