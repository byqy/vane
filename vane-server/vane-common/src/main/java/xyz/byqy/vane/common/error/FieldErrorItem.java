package xyz.byqy.vane.common.error;

/**
 * 字段级错误。pointer 为 RFC 6901 JSON Pointer，code 指向错误码注册表。
 */
public record FieldErrorItem(String pointer, String code) {
}
