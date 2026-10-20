package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.UUID;

/** 后台标签视图：带文章使用计数，便于判断能否删除。 */
public record TagView(UUID id, String code, long articleCount, Instant createdAt) {
}
