package xyz.byqy.vane.iam.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.domain.IamDept;
import xyz.byqy.vane.iam.domain.IamDeptRepository;
import xyz.byqy.vane.iam.domain.IamUserRepository;

/** 组织管理（ADM-03）：树形结构，删除前校验下级与成员。 */
@Service
public class DeptService {

    private final IamDeptRepository depts;
    private final IamUserRepository users;

    public DeptService(IamDeptRepository depts, IamUserRepository users) {
        this.depts = depts;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<DeptNode> tree() {
        List<IamDept> all = depts.findByDeletedAtIsNullOrderBySortNoAscCodeAsc();
        Set<UUID> ids = all.stream().map(IamDept::getId).collect(Collectors.toSet());

        Map<UUID, DeptNode> nodes = new LinkedHashMap<>();
        for (IamDept dept : all) {
            nodes.put(dept.getId(), new DeptNode(dept.getId(), dept.getParentId(), dept.getCode(),
                    dept.getName(), dept.getSortNo(), dept.getStatus(), new ArrayList<>()));
        }

        Map<UUID, List<DeptNode>> childrenByParent = new LinkedHashMap<>();
        List<DeptNode> roots = new ArrayList<>();
        for (IamDept dept : all) {
            DeptNode node = nodes.get(dept.getId());
            UUID parentId = dept.getParentId();
            if (parentId == null || !ids.contains(parentId)) {
                roots.add(node);
            } else {
                childrenByParent.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
            }
        }
        return roots.stream().map(root -> deep(root, childrenByParent)).toList();
    }

    private DeptNode deep(DeptNode node, Map<UUID, List<DeptNode>> childrenByParent) {
        List<DeptNode> children = childrenByParent.getOrDefault(node.id(), List.of());
        return new DeptNode(node.id(), node.parentId(), node.code(), node.name(), node.sortNo(),
                node.status(), children.stream().map(child -> deep(child, childrenByParent)).toList());
    }

    @Transactional
    public DeptNode create(UUID parentId, String code, String name, int sortNo) {
        if (depts.existsByCodeAndDeletedAtIsNull(code)) {
            throw new ApiException(CommonErrors.IAM_DEPT_CODE_TAKEN);
        }
        if (parentId != null && depts.findByIdAndDeletedAtIsNull(parentId).isEmpty()) {
            throw new ApiException(CommonErrors.IAM_DEPT_NOT_FOUND);
        }
        IamDept dept = IamDept.create(UUID.randomUUID(), parentId, code, name, sortNo);
        depts.save(dept);
        return toNode(dept);
    }

    @Transactional
    public DeptNode update(UUID id, UUID parentId, String name, String status, int sortNo) {
        IamDept dept = require(id);
        if (id.equals(parentId)) {
            throw new ApiException(CommonErrors.REQUEST_PRECONDITION_FAILED);
        }
        dept.update(parentId, name, status, sortNo);
        depts.save(dept);
        return toNode(dept);
    }

    @Transactional
    public void delete(UUID id) {
        IamDept dept = require(id);
        if (depts.existsByParentIdAndDeletedAtIsNull(id)) {
            throw new ApiException(CommonErrors.IAM_DEPT_HAS_CHILDREN);
        }
        if (users.countByDeptIdAndDeletedAtIsNull(id) > 0) {
            throw new ApiException(CommonErrors.IAM_DEPT_HAS_USERS);
        }
        dept.softDelete();
        depts.save(dept);
    }

    private DeptNode toNode(IamDept dept) {
        return new DeptNode(dept.getId(), dept.getParentId(), dept.getCode(), dept.getName(),
                dept.getSortNo(), dept.getStatus(), List.of());
    }

    private IamDept require(UUID id) {
        return depts.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.IAM_DEPT_NOT_FOUND));
    }
}
