package xyz.byqy.vane.portal;

import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.content.application.CategoryService;
import xyz.byqy.vane.content.application.PublicCategoryView;

/** 门户文章分类（PTL-02）：只返回启用分类，含已发布文章数。 */
@RestController
@RequestMapping("/api/v1/public/categories")
public class PublicCategoryController {

    private final CategoryService categories;

    public PublicCategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<PublicCategoryView> tree() {
        return categories.publicTree(LocaleContextHolder.getLocale().toLanguageTag());
    }
}
