package xyz.byqy.vane.api.admin;

import java.net.URI;
import java.util.List;
import java.util.UUID;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;
import xyz.byqy.vane.iam.application.PageResult;
import xyz.byqy.vane.iam.application.RoleCommandService;
import xyz.byqy.vane.iam.application.RoleQueryService;
import xyz.byqy.vane.iam.application.RoleSummary;

/** 角色管理（ADM-02）。 */
@RestController
@RequestMapping("/api/v1/admin/roles")
public class AdminRoleController {

    private final RoleQueryService queries;
    private final RoleCommandService commands;

    public AdminRoleController(RoleQueryService queries, RoleCommandService commands) {
        this.queries = queries;
        this.commands = commands;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_ROLE.code)")
    public PageResult<RoleSummary> list(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return queries.page(pageNumber, pageSize);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_ROLE_CREATE.code)")
    @Audit(module = "role", action = "create", targetType = "role", targetLabel = "#request.code()")
    public ResponseEntity<RoleSummary> create(@Valid @RequestBody CreateRoleRequest request) {
        RoleSummary created = commands.create(request.code(), request.name(), request.dataScope());
        return ResponseEntity.created(URI.create("/api/v1/admin/roles/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_ROLE_UPDATE.code)")
    @Audit(module = "role", action = "update", targetType = "role", targetId = "#id", detail = true)
    public RoleSummary update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return commands.update(id, request.name(), request.dataScope(), request.status(),
                request.sortNo() == null ? 0 : request.sortNo());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_ROLE_DELETE.code)")
    @Audit(module = "role", action = "delete", targetType = "role", targetId = "#id", detail = true)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        commands.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_ROLE.code)")
    public List<String> permissions(@PathVariable UUID id) {
        return commands.permissionCodes(id);
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_ROLE_ASSIGN.code)")
    @Audit(module = "role", action = "assign-permissions", targetType = "role", targetId = "#id", detail = true)
    public ResponseEntity<Void> assignPermissions(@PathVariable UUID id,
                                                  @Valid @RequestBody AssignPermissionsRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser actor) {
        commands.assignPermissions(id, request.permissionCodes(), actor == null ? null : actor.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/depts")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_ROLE.code)")
    public DeptsResponse depts(@PathVariable UUID id) {
        return new DeptsResponse(commands.customDeptIds(id));
    }

    public record DeptsResponse(java.util.List<UUID> deptIds) {
    }

    @PutMapping("/{id}/depts")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_ROLE_ASSIGN.code)")
    @Audit(module = "role", action = "assign-depts", targetType = "role", targetId = "#id", detail = true)
    public ResponseEntity<Void> assignDepts(@PathVariable UUID id, @Valid @RequestBody AssignDeptsRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        commands.assignCustomDepts(id, request.deptIds(), actor == null ? null : actor.id());
        return ResponseEntity.noContent().build();
    }
}




