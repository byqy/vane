package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 后台视图：包含全部语言版本，便于编辑。 */
public record NoticeView(UUID id, String level, String status, boolean pinned,
                          Instant publishAt, Instant expireAt, List<NoticeTranslation> translations) {
}
