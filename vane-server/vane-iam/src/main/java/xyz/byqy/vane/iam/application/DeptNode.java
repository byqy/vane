package xyz.byqy.vane.iam.application;

import java.util.List;
import java.util.UUID;

public record DeptNode(UUID id, UUID parentId, String code, String name, int sortNo,
                       String status, List<DeptNode> children) {
}
