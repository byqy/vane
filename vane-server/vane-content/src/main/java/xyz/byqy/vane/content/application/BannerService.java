package xyz.byqy.vane.content.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsBanner;
import xyz.byqy.vane.content.domain.CmsBannerRepository;

/**
 * 轮播 / 宣传位（ADM-08）：按位点与投放时间窗返回。
 * 文案双轨（ADR-025）：优先取 {@code cms_banner_i18n}，缺失回退 {@code title_key}。
 */
@Service
public class BannerService {

    private static final String DEFAULT_LOCALE = "zh-CN";

    private final CmsBannerRepository banners;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public BannerService(CmsBannerRepository banners, JdbcTemplate jdbc, Clock clock) {
        this.banners = banners;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<BannerView> list(String position, boolean onlyActive) {
        return list(position, onlyActive, null);
    }

    @Transactional(readOnly = true)
    public List<BannerView> list(String position, boolean onlyActive, String locale) {
        List<CmsBanner> all = position == null
                ? banners.findByDeletedAtIsNullOrderBySortNoAsc()
                : banners.findByPositionAndDeletedAtIsNullOrderBySortNoAsc(position);
        Instant now = clock.instant();
        Map<UUID, String> titles = titles(all, locale);
        return all.stream()
                .filter(banner -> !onlyActive || banner.isActiveAt(now))
                .map(banner -> new BannerView(banner.getId(), banner.getPosition(), banner.getImageFileId(),
                        banner.getTitleKey(), titles.get(banner.getId()), banner.getLinkUrl(), banner.getSortNo(),
                        banner.getStatus(), banner.getStartAt(), banner.getEndAt()))
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
        Map<UUID, String> titles = titles(List.of(banner), null);
        return new BannerView(banner.getId(), banner.getPosition(), banner.getImageFileId(), banner.getTitleKey(),
                titles.get(banner.getId()), banner.getLinkUrl(), banner.getSortNo(), banner.getStatus(),
                banner.getStartAt(), banner.getEndAt());
    }

    /** 按回退顺序取轮播文案：请求语言 → zh-CN → 首个可用。 */
    private Map<UUID, String> titles(List<CmsBanner> bannersInPage, String locale) {
        Map<UUID, String> result = new HashMap<>();
        if (bannersInPage.isEmpty()) {
            return result;
        }
        String placeholders = String.join(",", bannersInPage.stream().map(banner -> "?").toList());
        List<Object> args = new ArrayList<>(bannersInPage.stream().map(CmsBanner::getId).toList());
        jdbc.query("""
                select banner_id, locale, title
                  from cms_banner_i18n
                 where banner_id in (%s)
                 order by locale
                """.formatted(placeholders), rs -> {
            UUID bannerId = rs.getObject("banner_id", UUID.class);
            String rowLocale = rs.getString("locale");
            boolean preferred = locale != null && rowLocale.equalsIgnoreCase(locale);
            if (preferred || (result.get(bannerId) == null && rowLocale.equals(DEFAULT_LOCALE))) {
                result.put(bannerId, rs.getString("title"));
            } else if (result.get(bannerId) == null) {
                result.put(bannerId, rs.getString("title"));
            }
        }, args.toArray());
        return result;
    }

    private CmsBanner require(UUID id) {
        return banners.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_NOT_FOUND));
    }
}
