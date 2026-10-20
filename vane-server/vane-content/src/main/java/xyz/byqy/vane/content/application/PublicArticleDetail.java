package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 门户文章详情：正文按语言回退后直出。 */
public record PublicArticleDetail(UUID id, String slug, String title, String summary, String body,
                                  UUID coverFileId, Instant publishedAt, String categoryCode,
                                  String categoryName, List<String> tags) {
}
