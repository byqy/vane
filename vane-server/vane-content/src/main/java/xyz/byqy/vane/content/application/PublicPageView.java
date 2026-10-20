package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.UUID;

public record PublicPageView(UUID id, String slug, String title, String summary, String body,
                             UUID coverFileId, Instant publishedAt) {
}
