package xyz.byqy.vane.api.admin;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import xyz.byqy.vane.api.support.CsvExporter;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.common.audit.AuditContext;
import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;
import xyz.byqy.vane.iam.application.PageResult;
import xyz.byqy.vane.iam.application.UserCommandService;
import xyz.byqy.vane.iam.application.UserQueryService;
import xyz.byqy.vane.iam.application.UserRoleService;
import xyz.byqy.vane.iam.application.UserSummary;

/** 后台用户管理（ADM-01），写操作按权限点与 HTTP 语义（03 §3）实现。 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserQueryService queries;
    private final UserCommandService commands;
    private final UserRoleService userRoles;

    public AdminUserController(UserQueryService queries, UserCommandService commands,
                               UserRoleService userRoles) {
        this.queries = queries;
        this.commands = commands;
        this.userRoles = userRoles;
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_USER_EXPORT.code)")
    @Audit(module = "user", action = "export", targetType = "user")
    public void export(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal AuthenticatedUser actor,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        var rows = queries.exportRows(actor.id(), actor.deptId(), username, status).stream()
                .map(user -> java.util.List.of(
                        user.username(),
                        user.nickname() == null ? "" : user.nickname(),
                        user.deptName() == null ? "" : user.deptName(),
                        String.join("|", user.roles()),
                        user.status(),
                        String.valueOf(user.createdAt()),
                        user.lastLoginAt() == null ? "" : String.valueOf(user.lastLoginAt())))
                .toList();
        AuditContext.setAffectedCount(rows.size());
        CsvExporter.write(response, "users.csv",
                java.util.List.of("用户名", "昵称", "组织", "角色", "状态", "创建时间", "最后登录"), rows);
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_USER.code)")
    public PageResult<UserSummary> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String status,
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return queries.page(actor.id(), actor.deptId(), username, status, pageNumber, pageSize);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_USER.code)")
    public ResponseEntity<UserSummary> get(@PathVariable UUID id) {
        long version = commands.currentVersion(id);
        return ResponseEntity.ok().eTag(quoted(version)).body(commands.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_USER_CREATE.code)")
    @Audit(module = "user", action = "create", targetType = "user", targetLabel = "#request.username()")
    public ResponseEntity<UserSummary> create(@Valid @RequestBody CreateUserRequest request) {
        UserSummary created = commands.create(request.username(), request.password(),
                request.nickname(), request.deptId());
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_USER_UPDATE.code)")
    @Audit(module = "user", action = "update", targetType = "user", targetId = "#id", detail = true)
    public ResponseEntity<UserSummary> update(@PathVariable UUID id,
                                              @RequestHeader(name = "If-Match", required = false) String ifMatch,
                                              @Valid @RequestBody UpdateUserRequest request) {
        long expected = parseIfMatch(ifMatch);
        UserSummary updated = commands.update(id, expected, request.nickname(), request.deptId(), request.status());
        return ResponseEntity.ok().eTag(quoted(commands.currentVersion(id))).body(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_USER_DELETE.code)")
    @Audit(module = "user", action = "delete", targetType = "user", targetId = "#id", detail = true)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        commands.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_USER_RESET_PASSWORD.code)")
    @Audit(module = "user", action = "reset-password", targetType = "user", targetId = "#id", detail = true)
    public ResetPasswordResponse resetPassword(@PathVariable UUID id) {
        return new ResetPasswordResponse(commands.resetPassword(id));
    }

    @GetMapping("/{id}/roles")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_USER.code)")
    public RoleIdsResponse roles(@PathVariable UUID id) {
        return new RoleIdsResponse(userRoles.roleIdsOf(id));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_USER_UPDATE.code)")
    @Audit(module = "user", action = "assign-roles", targetType = "user", targetId = "#id", detail = true)
    public ResponseEntity<Void> assignRoles(@PathVariable UUID id,
                                            @Valid @RequestBody RoleIdsRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        var beforeRoles = userRoles.roleIdsOf(id);
        userRoles.replaceRoles(id, request.roleIds(), actor == null ? null : actor.id());
        AuditContext.record(Map.of("roleIds", beforeRoles), Map.of("roleIds", request.roleIds()), "roleIds");
        return ResponseEntity.noContent().build();
    }

    public record RoleIdsRequest(@NotNull List<UUID> roleIds) {
    }

    public record RoleIdsResponse(List<UUID> roleIds) {
    }

    private static String quoted(long version) {
        return "\"" + version + "\"";
    }

    private static long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(CommonErrors.REQUEST_PRECONDITION_FAILED);
        }
        try {
            return Long.parseLong(ifMatch.replace("\"", "").trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(CommonErrors.REQUEST_PRECONDITION_FAILED);
        }
    }
}







