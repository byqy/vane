package xyz.byqy.vane.content.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsNav;
import xyz.byqy.vane.content.domain.CmsNavRepository;

/** 前台导航（ADM-08）：树形，文案用 i18n key。 */
@Service
public class NavService {

    private final CmsNavRepository navs;

    public NavService(CmsNavRepository navs) {
        this.navs = navs;
    }

    @Transactional(readOnly = true)
    public List<NavView> tree(String position, boolean onlyActive) {
        List<CmsNav> all = position == null
                ? navs.findByDeletedAtIsNullOrderBySortNoAsc()
                : navs.findByPositionAndDeletedAtIsNullOrderBySortNoAsc(position);
        Map<UUID, List<NavView>> byParent = new LinkedHashMap<>();
        List<NavView> roots = new ArrayList<>();
        java.util.Set<UUID> ids = new java.util.HashSet<>();
        for (CmsNav nav : all) {
            ids.add(nav.getId());
        }
        for (CmsNav nav : all) {
            if (onlyActive && !"ACTIVE".equals(nav.getStatus())) {
                continue;
            }
            NavView view = new NavView(nav.getId(), nav.getParentId(), nav.getPosition(), nav.getLabelKey(),
                    nav.getTarget(), nav.isOpenInNewTab(), nav.getSortNo(), nav.getStatus(), new ArrayList<>());
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
        return new NavView(node.id(), node.parentId(), node.position(), node.labelKey(), node.target(),
                node.openInNewTab(), node.sortNo(), node.status(),
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
        return new NavView(nav.getId(), nav.getParentId(), nav.getPosition(), nav.getLabelKey(), nav.getTarget(),
                nav.isOpenInNewTab(), nav.getSortNo(), nav.getStatus(), List.of());
    }

    private CmsNav require(UUID id) {
        return navs.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_NOT_FOUND));
    }
}
