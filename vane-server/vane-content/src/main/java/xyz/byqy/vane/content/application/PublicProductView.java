package xyz.byqy.vane.content.application;

import java.util.List;

/** 门户产品列表项：按请求语言取名称与摘要。 */
public record PublicProductView(String id, String code, String slug, String group,
                                String coverFileId, String videoFileId, String name, String summary,
                                int sortNo, String publishedAt) {
}
