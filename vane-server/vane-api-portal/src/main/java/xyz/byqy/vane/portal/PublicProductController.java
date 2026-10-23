package xyz.byqy.vane.portal;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.content.application.PageResult;
import xyz.byqy.vane.content.application.ProductService;
import xyz.byqy.vane.content.application.PublicProductDetail;
import xyz.byqy.vane.content.application.PublicProductView;

/**
 * 门户产品中心（PTL-10）：只返回已发布产品，按 Accept-Language 取语言版本；
 * 支持按产品线（group）过滤。
 */
@RestController
@RequestMapping("/api/v1/public/products")
public class PublicProductController {

    private final ProductService products;

    public PublicProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public PageResult<PublicProductView> list(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String group) {
        return products.publicPage(group, locale(), pageNumber, pageSize);
    }

    @GetMapping("/{slug}")
    public PublicProductDetail bySlug(@PathVariable String slug) {
        return products.publicBySlug(slug, locale());
    }

    private String locale() {
        return LocaleContextHolder.getLocale().toLanguageTag();
    }
}
