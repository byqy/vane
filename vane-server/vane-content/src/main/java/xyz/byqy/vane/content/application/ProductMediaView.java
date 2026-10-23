package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.UUID;

/** 后台产品媒体项（图集 / 视频）。 */
public record ProductMediaView(UUID id, UUID productId, UUID fileId, String kind, int sortNo, String caption,
                               Instant updatedAt) {
}
