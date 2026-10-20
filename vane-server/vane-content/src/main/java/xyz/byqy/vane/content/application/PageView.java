package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PageView(UUID id, String slug, String status, UUID coverFileId, Instant publishedAt,
                       List<PageTranslation> translations) {
}
