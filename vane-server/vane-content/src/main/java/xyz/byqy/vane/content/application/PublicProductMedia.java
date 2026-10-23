package xyz.byqy.vane.content.application;

/** 门户产品媒体项（图集 / 视频），前端拼 /api/v1/public/files/{fileId} 取流。 */
public record PublicProductMedia(String fileId, String kind, int sortNo, String caption) {
}
