package xyz.byqy.vane.portal;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.content.application.BannerService;
import xyz.byqy.vane.content.application.BannerView;
import xyz.byqy.vane.content.application.NavService;
import xyz.byqy.vane.content.application.NavView;

/** 门户导航与轮播（PTL-01）：只返回启用且处于投放时间窗的数据。 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicContentController {

    private final NavService navs;
    private final BannerService banners;

    public PublicContentController(NavService navs, BannerService banners) {
        this.navs = navs;
        this.banners = banners;
    }

    @GetMapping("/navs")
    public List<NavView> navs(@RequestParam(defaultValue = "HEADER") String position) {
        return navs.tree(position, true);
    }

    @GetMapping("/banners")
    public List<BannerView> banners(@RequestParam(defaultValue = "home.top") String position) {
        return banners.list(position, true);
    }
}
