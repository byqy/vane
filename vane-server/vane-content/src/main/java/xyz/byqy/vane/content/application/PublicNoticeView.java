package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.UUID;

/** 门户视图：按请求语言返回单条文案。 */
public record PublicNoticeView(UUID id, String level, boolean pinned, String title, String body,
                               Instant publishAt) {
}
