package xyz.byqy.vane.system.application;

import java.util.Map;

public record DictItemView(String value, String label, String color, int sortNo, String status,
                           boolean builtin, Map<String, String> labels) {
}
