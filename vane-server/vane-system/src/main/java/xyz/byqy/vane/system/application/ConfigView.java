package xyz.byqy.vane.system.application;

/** valueJson 为原始 JSON 文本，前端按 valueType 解析。 */
public record ConfigView(String key, String valueJson, String valueType, String description, boolean editable) {
}
