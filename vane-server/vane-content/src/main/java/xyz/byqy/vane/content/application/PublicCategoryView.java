package xyz.byqy.vane.content.application;

import java.util.List;
import java.util.UUID;

/** 门户分类视图：按请求语言取名称与描述，并带已发布文章数。 */
public record PublicCategoryView(UUID id, String code, String name, String description, long articleCount,
                                 List<PublicCategoryView> children) {
}
