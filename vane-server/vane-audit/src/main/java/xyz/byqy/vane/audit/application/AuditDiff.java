package xyz.byqy.vane.audit.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;
import xyz.byqy.vane.common.audit.AuditContext;

/** 字段级变更计算（ADR-023 §3）：白名单、只保留变化、脱敏、100 字段/16KB 截断。 */
@Component
public class AuditDiff {

    private static final int MAX_FIELDS = 100;
    private static final int MAX_BYTES = 16 * 1024;

    private final ObjectMapper json;
    private final AuditMasker masker;

    public AuditDiff(ObjectMapper json, AuditMasker masker) {
        this.json = json;
        this.masker = masker;
    }

    public Result diff(AuditContext context) {
        if (context == null) {
            return null;
        }
        List<ObjectNode> changes = new ArrayList<>();
        boolean truncated = false;
        for (AuditContext.ChangeRecord record : context.records()) {
            JsonNode before = record.before() == null ? null : json.valueToTree(record.before());
            JsonNode after = record.after() == null ? null : json.valueToTree(record.after());
            Set<String> fields = record.fields().isEmpty()
                    ? unionLeafFields(before, after)
                    : new LinkedHashSet<>(record.fields());
            for (String field : fields) {
                JsonNode beforeValue = read(before, field);
                JsonNode afterValue = read(after, field);
                if (same(beforeValue, afterValue)) {
                    continue;
                }
                if (changes.size() >= MAX_FIELDS) {
                    truncated = true;
                    break;
                }
                ObjectNode change = json.createObjectNode();
                change.put("field", field);
                change.set("before", maskValue(field, beforeValue));
                change.set("after", maskValue(field, afterValue));
                changes.add(change);
            }
            if (truncated) {
                break;
            }
        }
        ArrayNode array = json.createArrayNode();
        changes.forEach(array::add);
        String serialized = json.writeValueAsString(array);
        if (serialized.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
            truncated = true;
            while (!changes.isEmpty()
                    && json.writeValueAsString(changes).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) {
                changes.remove(changes.size() - 1);
            }
            serialized = json.writeValueAsString(changes);
        }
        return new Result(serialized, truncated);
    }

    private JsonNode maskValue(String field, JsonNode value) {
        if (value == null || value.isNull()) {
            return value;
        }
        if (masker.isSensitiveField(field)) {
            return StringNode.valueOf("***");
        }
        if (value.isTextual()) {
            return StringNode.valueOf(masker.maskText(value.asString()));
        }
        if (value.isArray() || value.isObject()) {
            try {
                return json.readTree(masker.maskJson(json.writeValueAsString(value)));
            } catch (RuntimeException ex) {
                return StringNode.valueOf("***");
            }
        }
        return value;
    }

    private Set<String> unionLeafFields(JsonNode before, JsonNode after) {
        Set<String> fields = new LinkedHashSet<>();
        collect(before, "", fields);
        collect(after, "", fields);
        return fields;
    }

    private void collect(JsonNode node, String prefix, Set<String> fields) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                if (entry.getValue().isObject() || entry.getValue().isArray()) {
                    collect(entry.getValue(), path, fields);
                } else {
                    fields.add(path);
                }
            });
        } else if (node.isArray()) {
            fields.add(prefix);
        } else if (!prefix.isEmpty()) {
            fields.add(prefix);
        }
    }

    private JsonNode read(JsonNode tree, String field) {
        if (tree == null) {
            return null;
        }
        JsonNode current = tree;
        for (String part : field.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    private boolean same(JsonNode left, JsonNode right) {
        if (left == null || left.isNull()) {
            return right == null || right.isNull();
        }
        return left.equals(right);
    }

    public record Result(String changesJson, boolean truncated) {
    }
}
