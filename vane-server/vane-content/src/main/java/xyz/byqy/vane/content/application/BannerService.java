package xyz.byqy.vane.content.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsBanner;
import xyz.byqy.vane.content.domain.CmsBannerRepository;

/** 轮播 / 宣传位（ADM-08）：按位点与投放时间窗返回。 */
@Service
public class BannerService {

    private final CmsBannerRepository banners;
    private final Clock clock;

    public BannerService(CmsBannerRepository banners, Clock clock) {
        this.banners = banners;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BannerView> list(String position, boolean onlyActive) {
        List<CmsBanner> all = position == null
                ? banners.findByDeletedAtIsNullOrderBySortNoAsc()
                : banners.findByPositionAndDeletedAtIsNullOrderBySortNoAsc(position);
        Instant now = clock.instant();
        return all.stream()
                .filter(banner -> !onlyActive || banner.isActiveAt(now))
                .map(this::toView)
                .toList();
    }

    @Transactional
    public BannerView create(String position, UUID imageFileId, String titleKey, String linkUrl, int sortNo,
                             Instant startAt, Instant endAt) {
        CmsBanner banner = CmsBanner.create(UUID.randomUUID(), position, imageFileId, titleKey, linkUrl,
                sortNo, startAt, endAt);
        banners.save(banner);
        return toView(banner);
    }

    @Transactional
    public BannerView update(UUID id, UUID imageFileId, String titleKey, String linkUrl, int sortNo,
                             String status, Instant startAt, Instant endAt) {
        CmsBanner banner = require(id);
        banner.update(imageFileId, titleKey, linkUrl, sortNo, status, startAt, endAt);
        banners.save(banner);
        return toView(banner);
    }

    @Transactional
    public void delete(UUID id) {
        CmsBanner banner = require(id);
        banner.softDelete();
        banners.save(banner);
    }

    private BannerView toView(CmsBanner banner) {
        return new BannerView(banner.getId(), banner.getPosition(), banner.getImageFileId(), banner.getTitleKey(),
                banner.getLinkUrl(), banner.getSortNo(), banner.getStatus(), banner.getStartAt(), banner.getEndAt());
    }

    private CmsBanner require(UUID id) {
        return banners.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_NOT_FOUND));
    }
}
