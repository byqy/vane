package xyz.byqy.vane.content.application;

import java.util.List;
import java.util.UUID;

public record NavView(UUID id, UUID parentId, String position, String labelKey, String target,
                      boolean openInNewTab, int sortNo, String status, List<NavView> children) {
}
