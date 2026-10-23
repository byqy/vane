package xyz.byqy.vane.portal.seo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.i18n.MessageResolver;
import xyz.byqy.vane.content.application.ProductService;
import xyz.byqy.vane.content.domain.CmsArticle;
import xyz.byqy.vane.content.domain.CmsArticleRepository;
import xyz.byqy.vane.content.domain.CmsPage;
import xyz.byqy.vane.content.domain.CmsPageRepository;
import xyz.byqy.vane.system.application.SysConfigService;

/**
 * 门户 SEO 基础（PTL-08）：站点元信息、可索引内容清单、sitemap 与 robots。
 * 门户渲染仍是 SPA，SEO 依赖页面动态元数据 + 这里的可爬取清单。
 */
@Service
public class PortalSeoService {

    private static final String DEFAULT_LOCALE = "zh-CN";

    private final SysConfigService configs;
    private final CmsPageRepository pages;
    private final CmsArticleRepository articles;
    private final ProductService products;
    private final MessageResolver messages;

    public PortalSeoService(SysConfigService configs, CmsPageRepository pages, CmsArticleRepository articles,
                            ProductService products, MessageResolver messages) {
        this.configs = configs;
        this.pages = pages;
        this.articles = articles;
        this.products = products;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public SiteSeoView site() {
        return new SiteSeoView(
                configs.getString("seo.site-name", "vane"),
                configs.getString("seo.description", ""),
                baseUrl(),
                configs.getString("seo.og-image", ""),
                List.copyOf(messages.supportedLocales()),
                DEFAULT_LOCALE,
                configs.getString("site.contact.email", ""),
                configs.getString("site.contact.phone", ""),
                configs.getString("site.contact.address", ""),
                configs.getString("site.contact.hours", ""));
    }

    /** 已发布内容页清单（sitemap 与站内导航使用）。 */
    @Transactional(readOnly = true)
    public List<PageLink> pages() {
        List<PageLink> links = new ArrayList<>();
        for (CmsPage page : pages.findAllByStatusAndDeletedAtIsNullOrderByPublishedAtDesc("PUBLISHED")) {
            links.add(new PageLink(page.getSlug(), page.getPublishedAt()));
        }
        return links;
    }

    /** 多语言 sitemap：首页、内容页与文章 × 全部支持语言，并给出 hreflang 互指。 */
    @Transactional(readOnly = true)
    public String sitemap() {
        String base = baseUrl();
        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"")
                .append(" xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">\n");

        for (String locale : messages.supportedLocales()) {
            appendUrl(xml, base + "/" + locale, null, locale);
        }
        for (CmsPage page : pages.findAllByStatusAndDeletedAtIsNullOrderByPublishedAtDesc("PUBLISHED")) {
            String lastmod = page.getPublishedAt() == null ? null : page.getPublishedAt().toString();
            for (String locale : messages.supportedLocales()) {
                appendUrl(xml, base + "/" + locale + "/" + page.getSlug(), lastmod, locale);
            }
        }
        for (CmsArticle article : articles.findAllByStatusAndDeletedAtIsNullOrderByPublishedAtDesc("PUBLISHED")) {
            String lastmod = article.getPublishedAt() == null ? null : article.getPublishedAt().toString();
            for (String locale : messages.supportedLocales()) {
                appendUrl(xml, base + "/" + locale + "/articles/" + article.getSlug(), lastmod, locale);
            }
        }
        for (ProductService.ProductLink product : products.publishedLinks()) {
            String lastmod = product.publishedAt() == null ? null : product.publishedAt().toString();
            for (String locale : messages.supportedLocales()) {
                appendUrl(xml, base + "/" + locale + "/products/" + product.slug(), lastmod, locale);
            }
        }
        xml.append("</urlset>\n");
        return xml.toString();
    }

    public String robots() {
        return "User-agent: *\n"
                + "Allow: /\n"
                + "Disallow: /admin/\n"
                + "Sitemap: " + baseUrl() + "/sitemap.xml\n";
    }

    private void appendUrl(StringBuilder xml, String loc, String lastmod, String locale) {
        xml.append("  <url><loc>").append(escape(loc)).append("</loc>");
        if (lastmod != null) {
            xml.append("<lastmod>").append(escape(lastmod)).append("</lastmod>");
        }
        for (String alternate : messages.supportedLocales()) {
            xml.append("<xhtml:link rel=\"alternate\" hreflang=\"").append(escape(alternate))
                    .append("\" href=\"").append(escape(alternateUrl(loc, locale, alternate))).append("\"/>");
        }
        xml.append("</url>\n");
    }

    /** 把 loc 的 locale 段替换为目标语言，用于 hreflang 互指。 */
    private String alternateUrl(String loc, String locale, String alternate) {
        String suffix = "/" + locale;
        if (loc.endsWith(suffix)) {
            return loc.substring(0, loc.length() - suffix.length()) + "/" + alternate;
        }
        return loc.replace("/" + locale + "/", "/" + alternate + "/");
    }

    private String baseUrl() {
        String configured = configs.getString("seo.base-url", "http://localhost:5175");
        String trimmed = configured == null ? "" : configured.strip();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static String escape(String value) {
        return value == null ? ""
                : value.replace("&", "&amp;").replace("<", "&lt;")
                        .replace(">", "&gt;").replace("\"", "&quot;");
    }

    public record SiteSeoView(String siteName, String description, String baseUrl, String ogImage,
                              List<String> locales, String defaultLocale, String contactEmail, String contactPhone,
                              String contactAddress, String contactHours) {
    }

    public record PageLink(String slug, Instant publishedAt) {
    }

}

