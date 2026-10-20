package xyz.byqy.vane.portal;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.content.application.ContentPageService;
import xyz.byqy.vane.content.application.PublicPageView;

/** 门户内容页（PTL-02）：按 slug 取已发布内容，按 Accept-Language 返回语言版本。 */
@RestController
@RequestMapping("/api/v1/public/pages")
public class PublicPageController {

    private final ContentPageService pages;

    public PublicPageController(ContentPageService pages) {
        this.pages = pages;
    }

    @GetMapping("/{slug}")
    public PublicPageView bySlug(@PathVariable String slug) {
        return pages.publicBySlug(slug, LocaleContextHolder.getLocale().toLanguageTag());
    }
}
