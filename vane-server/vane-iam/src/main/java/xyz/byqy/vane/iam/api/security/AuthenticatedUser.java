package xyz.byqy.vane.iam.api.security;

import java.util.UUID;

/** 认证主体：只携带后续授权所需的最小信息。 */
public record AuthenticatedUser(UUID id, String username, UUID deptId) {
}
