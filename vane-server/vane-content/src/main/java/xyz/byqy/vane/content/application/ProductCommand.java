package xyz.byqy.vane.content.application;

import java.util.List;

/** 产品保存命令：后台创建 / 更新共用（媒体集可选，也可用单独接口整体替换）。 */
public record ProductCommand(String code, String slug, String group, String coverFileId, String videoFileId,
                             String status, int sortNo, List<ProductTranslation> translations,
                             List<PublicProductMedia> media) {

    public ProductCommand {
        translations = translations == null ? List.of() : List.copyOf(translations);
        media = media == null ? List.of() : List.copyOf(media);
    }
}
