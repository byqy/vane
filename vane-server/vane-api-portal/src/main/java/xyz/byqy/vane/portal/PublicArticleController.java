package xyz.byqy.vane.portal;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.content.application.ArticleService;
import xyz.byqy.vane.content.application.PageResult;
import xyz.byqy.vane.content.application.PublicArticleDetail;
import xyz.byqy.vane.content.application.PublicArticleView;

/**
 * 门户文章（PTL-02）：只返回已发布文章，按 Accept-Language 取语言版本；
 * 支持分类 code、标签 code 与关键词（ILIKE + pg_trgm）过滤。
 */
@RestController
@RequestMapping("/api/v1/public/articles")
public class PublicArticleController {

    private final ArticleService articles;

    public PublicArticleController(ArticleService articles) {
        this.articles = articles;
    }

    @GetMapping
    public PageResult<PublicArticleView> list(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword) {
        return articles.publicPage(locale(), category, tag, keyword, pageNumber, pageSize);
    }

    @GetMapping("/{slug}")
    public PublicArticleDetail bySlug(@PathVariable String slug) {
        return articles.publicBySlug(slug, locale());
    }

    private String locale() {
        return LocaleContextHolder.getLocale().toLanguageTag();
    }
}
