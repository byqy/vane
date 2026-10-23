package xyz.byqy.vane.content.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsNav;
import xyz.byqy.vane.content.domain.CmsNavRepository;

/**
 * 前台导航（ADM-08）：树形。
 * 文案双轨（ADR-025）：优先取 {@code cms_nav_i18n}，缺失回退 {@code label_key}（前端再翻译）。
 */
@Service
public class NavService {

    private static final String DEFAULT_LOCALE = "zh-CN";

    private final CmsNavRepository navs;
    private final JdbcTemplate jdbc;

    public NavService(CmsNavRepository navs, JdbcTemplate jdbc) {
        this.navs = navs;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<NavView> tree(String position, boolean onlyActive) {
        return tree(position, onlyActive, null);
    }

    @Transactional(readOnly = true)
    public List<NavView> tree(String position, boolean onlyActive, String locale) {
        List<CmsNav> all = position == null
                ? navs.findByDeletedAtIsNullOrderBySortNoAsc()
                : navs.findByPositionAndDeletedAtIsNullOrderBySortNoAsc(position);
        Map<UUID, String> labels = labels(all, locale);
        Map<UUID, List<NavView>> byParent = new LinkedHashMap<>();
        List<NavView> roots = new ArrayList<>();
        Set<UUID> ids = new HashSet<>();
        for (CmsNav nav : all) {
            ids.add(nav.getId());
        }
        for (CmsNav nav : all) {
            if (onlyActive && !"ACTIVE".equals(nav.getStatus())) {
                continue;
            }
            NavView view = new NavView(nav.getId(), nav.getParentId(), nav.getPosition(), nav.getLabelKey(),
                    labels.get(nav.getId()), nav.getTarget(), nav.isOpenInNewTab(), nav.getSortNo(),
                    nav.getStatus(), new ArrayList<>());
            if (nav.getParentId() == null || !ids.contains(nav.getParentId())) {
                roots.add(view);
            } else {
                byParent.computeIfAbsent(nav.getParentId(), key -> new ArrayList<>()).add(view);
            }
        }
        return roots.stream().map(root -> deep(root, byParent)).toList();
    }

    private NavView deep(NavView node, Map<UUID, List<NavView>> byParent) {
        List<NavView> children = byParent.getOrDefault(node.id(), List.of());
        return new NavView(node.id(), node.parentId(), node.position(), node.labelKey(), node.label(),
                node.target(), node.openInNewTab(), node.sortNo(), node.status(),
                children.stream().map(child -> deep(child, byParent)).toList());
    }

    @Transactional
    public NavView create(UUID parentId, String position, String labelKey, String target,
                          boolean openInNewTab, int sortNo) {
        CmsNav nav = CmsNav.create(UUID.randomUUID(), parentId, position, labelKey, target, openInNewTab, sortNo);
        navs.save(nav);
        return toView(nav);
    }

    @Transactional
    public NavView update(UUID id, UUID parentId, String labelKey, String target, boolean openInNewTab,
                          int sortNo, String status) {
        CmsNav nav = require(id);
        if (id.equals(parentId)) {
            throw new ApiException(CommonErrors.REQUEST_PRECONDITION_FAILED);
        }
        nav.update(parentId, labelKey, target, openInNewTab, sortNo, status);
        navs.save(nav);
        return toView(nav);
    }

    @Transactional
    public void delete(UUID id) {
        CmsNav nav = require(id);
        if (navs.existsByParentIdAndDeletedAtIsNull(id)) {
            throw new ApiException(CommonErrors.REQUEST_PRECONDITION_FAILED);
        }
        nav.softDelete();
        navs.save(nav);
    }

    private NavView toView(CmsNav nav) {
        Map<UUID, String> labels = labels(List.of(nav), null);
        return new NavView(nav.getId(), nav.getParentId(), nav.getPosition(), nav.getLabelKey(),
                labels.get(nav.getId()), nav.getTarget(), nav.isOpenInNewTab(), nav.getSortNo(),
                nav.getStatus(), List.of());
    }

    /** 按回退顺序取导航文案：请求语言 → zh-CN → 无（由前端用 label_key 翻译）。 */
    private Map<UUID, String> labels(List<CmsNav> navsInPage, String locale) {
        Map<UUID, String> result = new HashMap<>();
        if (navsInPage.isEmpty()) {
            return result;
        }
        List<Object> args = new ArrayList<>();
        String placeholders = String.join(",", navsInPage.stream().map(nav -> "?").toList());
        navsInPage.forEach(nav -> args.add(nav.getId()));
        jdbc.query("""
                select nav_id, locale, label
                  from cms_nav_i18n
                 where nav_id in (%s)
                 order by locale
                """.formatted(placeholders), rs -> {
            UUID navId = rs.getObject("nav_id", UUID.class);
            String rowLocale = rs.getString("locale");
            boolean preferred = locale != null && rowLocale.equalsIgnoreCase(locale);
            if (preferred || (result.get(navId) == null && rowLocale.equals(DEFAULT_LOCALE))) {
                result.put(navId, rs.getString("label"));
            } else if (result.get(navId) == null) {
                result.put(navId, rs.getString("label"));
            }
        }, args.toArray());
        return result;
    }

    private CmsNav require(UUID id) {
        return navs.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_NOT_FOUND));
    }
}
