package xyz.byqy.vane.content.application;

import java.util.List;

/** 产品多语言内容：名称、摘要、正文与特性清单。 */
public record ProductTranslation(String locale, String name, String summary, String body,
                                 List<ProductFeature> features) {

    public ProductTranslation {
        features = features == null ? List.of() : List.copyOf(features);
    }
}
