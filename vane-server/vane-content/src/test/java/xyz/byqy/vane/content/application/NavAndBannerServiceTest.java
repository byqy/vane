package xyz.byqy.vane.content.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsBanner;
import xyz.byqy.vane.content.domain.CmsBannerRepository;
import xyz.byqy.vane.content.domain.CmsNav;
import xyz.byqy.vane.content.domain.CmsNavRepository;

class NavAndBannerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T00:00:00Z");

    private final CmsNavRepository navs = mock(CmsNavRepository.class);
    private final CmsBannerRepository banners = mock(CmsBannerRepository.class);
    private final NavService navService = new NavService(navs);
    private final BannerService bannerService = new BannerService(banners, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void navTreeNestsChildren() {
        CmsNav root = CmsNav.create(UUID.randomUUID(), null, "HEADER", "nav.home", "/", false, 10);
        CmsNav child = CmsNav.create(UUID.randomUUID(), root.getId(), "HEADER", "nav.about", "/about", false, 20);
        when(navs.findByPositionAndDeletedAtIsNullOrderBySortNoAsc("HEADER")).thenReturn(List.of(root, child));

        List<NavView> tree = navService.tree("HEADER", true);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).labelKey()).isEqualTo("nav.home");
        assertThat(tree.get(0).children()).hasSize(1);
    }

    @Test
    void navDeleteRejectsNodeWithChildren() {
        CmsNav root = CmsNav.create(UUID.randomUUID(), null, "HEADER", "nav.home", "/", false, 10);
        when(navs.findByIdAndDeletedAtIsNull(root.getId())).thenReturn(Optional.of(root));
        when(navs.existsByParentIdAndDeletedAtIsNull(root.getId())).thenReturn(true);

        assertThatThrownBy(() -> navService.delete(root.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.REQUEST_PRECONDITION_FAILED);
    }

    @Test
    void bannerOutsideTimeWindowIsHidden() {
        CmsBanner future = CmsBanner.create(UUID.randomUUID(), "home.top", UUID.randomUUID(), "banner.a", "/",
                10, NOW.plusSeconds(3600), null);
        CmsBanner active = CmsBanner.create(UUID.randomUUID(), "home.top", UUID.randomUUID(), "banner.b", "/",
                20, NOW.minusSeconds(3600), null);
        when(banners.findByPositionAndDeletedAtIsNullOrderBySortNoAsc("home.top"))
                .thenReturn(List.of(future, active));

        List<BannerView> visible = bannerService.list("home.top", true);

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).titleKey()).isEqualTo("banner.b");
    }
}
