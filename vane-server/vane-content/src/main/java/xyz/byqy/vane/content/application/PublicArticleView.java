package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 门户文章列表项：不返回正文，减少列表响应体积。 */
public record PublicArticleView(UUID id, String slug, String title, String summary, UUID coverFileId,
                                Instant publishedAt, String categoryCode, String categoryName,
                                List<String> tags) {
}
