package xyz.byqy.vane.portal.seo;

import java.time.Duration;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.portal.seo.PortalSeoService.PageLink;
import xyz.byqy.vane.portal.seo.PortalSeoService.SiteSeoView;

/**
 * 门户 SEO 基础接口（PTL-08）：
 * /api/v1/public/site、/api/v1/public/pages 供前端；/sitemap.xml、/robots.txt 供爬虫。
 */
@RestController
public class PublicSeoController {

    private static final CacheControl PUBLIC_ONE_HOUR = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();

    private final PortalSeoService seo;

    public PublicSeoController(PortalSeoService seo) {
        this.seo = seo;
    }

    @GetMapping("/api/v1/public/site")
    public SiteSeoView site() {
        return seo.site();
    }

    @GetMapping("/api/v1/public/pages")
    public List<PageLink> pages() {
        return seo.pages();
    }

    @GetMapping(value = {"/api/v1/public/sitemap.xml", "/sitemap.xml"}, produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML)
                .cacheControl(PUBLIC_ONE_HOUR).body(seo.sitemap());
    }

    @GetMapping(value = {"/api/v1/public/robots.txt", "/robots.txt"}, produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> robots() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN)
                .cacheControl(PUBLIC_ONE_HOUR).body(seo.robots());
    }
}
