package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.UUID;

public record BannerView(UUID id, String position, UUID imageFileId, String titleKey, String linkUrl,
                         int sortNo, String status, Instant startAt, Instant endAt) {
}
