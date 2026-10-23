package xyz.byqy.vane.content.application;

import java.util.List;
import java.util.UUID;

/** 前台导航（ADM-08）：树形；文案优先取 cms_nav_i18n，缺失回退 label_key。 */
public record NavView(UUID id, UUID parentId, String position, String labelKey, String label, String target,
                      boolean openInNewTab, int sortNo, String status, List<NavView> children) {
}
