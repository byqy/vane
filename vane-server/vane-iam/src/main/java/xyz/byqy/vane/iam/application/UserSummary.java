package xyz.byqy.vane.iam.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 用户视图：列表与详情共用，含组织名、角色码与版本号（版本用于 If-Match）。 */
public record UserSummary(UUID id, String username, String nickname, UUID deptId, String deptName,
                          String status, List<String> roles, Instant createdAt, Instant lastLoginAt,
                          long version) {
}
