package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.UUID;

/** 轮播 / 宣传位（ADM-08）：文案优先取 cms_banner_i18n，缺失回退 title_key。 */
public record BannerView(UUID id, String position, UUID imageFileId, String titleKey, String title, String linkUrl,
                         int sortNo, String status, Instant startAt, Instant endAt) {
}
