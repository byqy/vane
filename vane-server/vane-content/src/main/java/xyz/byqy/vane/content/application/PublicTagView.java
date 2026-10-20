package xyz.byqy.vane.content.application;

import java.util.UUID;

/** 门户标签视图：带已发布文章计数。 */
public record PublicTagView(UUID id, String code, long articleCount) {
}
