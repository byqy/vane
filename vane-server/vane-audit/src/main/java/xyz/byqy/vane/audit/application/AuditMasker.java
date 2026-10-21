package xyz.byqy.vane.audit.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;
import xyz.byqy.vane.audit.api.Audit;

/** 审计脱敏（ADR-023 §4）：键名规则 + 值规则 + 字符串化 JSON 递归。 */
@Component
public class AuditMasker {

    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(password|secret|token|accesskey|secretkey|callbacktoken|recoverycode).*");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(\\+?\\d{2,3}[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{6,14}");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-]{1,2})[A-Za-z0-9._%+-]*(@[A-Za-z0-9.-]+)");
    private static final Pattern ID_CARD = Pattern.compile("(?<!\\d)(\\d{4})\\d{10}(\\d{3}[0-9Xx])(?!\\d)");
    private static final int MAX_PARAMS_BYTES = 8 * 1024;
    private static final int MAX_UA = 512;

    private final ObjectMapper json;

    public AuditMasker(ObjectMapper json) {
        this.json = json;
    }

    public boolean isSensitiveField(String field) {
        String leaf = field == null ? "" : field.substring(field.lastIndexOf('.') + 1);
        return "code".equalsIgnoreCase(leaf) || SENSITIVE_KEY.matcher(leaf).matches();
    }

    /** 入参快照：过滤 Servlet 对象、脱敏、8KB 截断。 */
    public String maskParams(Object[] args) {
        try {
            List<Object> values = new ArrayList<>();
            for (Object arg : args) {
                if (arg != null && !(arg instanceof jakarta.servlet.ServletRequest)
                        && !(arg instanceof jakarta.servlet.ServletResponse)) {
                    values.add(arg);
                }
            }
            String raw = json.writeValueAsString(values);
            if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PARAMS_BYTES) {
                return "{\"__truncated__\":true}";
            }
            return maskJson(raw);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** 对 JSON 字符串做递归脱敏；字符串化 JSON 会继续解析后处理。 */
    public String maskJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        try {
            JsonNode node = json.readTree(raw);
            return json.writeValueAsString(maskNode(node));
        } catch (RuntimeException ex) {
            return "\"***\"";
        }
    }

    private JsonNode maskNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isArray()) {
            var array = json.createArrayNode();
            node.forEach(item -> array.add(maskNode(item)));
            return array;
        }
        if (node.isObject()) {
            ObjectNode object = json.createObjectNode();
            node.properties().forEach(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (isSensitiveField(key)) {
                    object.put(key, "***");
                } else {
                    object.set(key, maskNode(value));
                }
            });
            return object;
        }
        if (node.isTextual()) {
            String text = node.asString();
            if (looksLikeJson(text)) {
                try {
                    return maskNode(json.readTree(text));
                } catch (RuntimeException ignored) {
                    return StringNode.valueOf(maskText(text));
                }
            }
            return StringNode.valueOf(maskText(text));
        }
        return node;
    }

    private boolean looksLikeJson(String text) {
        String trimmed = text.strip();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    /** 值级掩码：手机号/邮箱/证件号保留首尾。 */
    public String maskText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String masked = PHONE.matcher(value).replaceAll(match -> maskPhone(match.group()));
        masked = E164.matcher(masked).replaceAll(match -> maskPhone(match.group()));
        masked = EMAIL.matcher(masked).replaceAll(match ->
                match.group(1) + "***" + match.group(2));
        masked = ID_CARD.matcher(masked).replaceAll(match ->
                match.group(1) + "**********" + match.group(2));
        return masked;
    }

    public String truncateUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() <= MAX_UA ? userAgent : userAgent.substring(0, MAX_UA);
    }

    private static String maskPhone(String value) {
        int digits = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                digits++;
            }
        }
        if (digits <= 7) {
            return "***";
        }
        int keep = digits <= 11 ? 3 : 4;
        StringBuilder result = new StringBuilder();
        int seen = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                seen++;
                result.append(seen <= keep || seen > digits - 4 ? c : '*');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    static int maxParamsBytes() {
        return MAX_PARAMS_BYTES;
    }
}
