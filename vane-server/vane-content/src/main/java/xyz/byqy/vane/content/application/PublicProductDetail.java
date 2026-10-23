package xyz.byqy.vane.content.application;

import java.util.List;

/** 门户产品详情：名称/摘要/正文/特性/媒体均按请求语言准备就绪。 */
public record PublicProductDetail(String id, String code, String slug, String group,
                                  String coverFileId, String videoFileId, String name, String summary,
                                  String body, List<ProductFeature> features, List<PublicProductMedia> media,
                                  int sortNo, String publishedAt) {
}
