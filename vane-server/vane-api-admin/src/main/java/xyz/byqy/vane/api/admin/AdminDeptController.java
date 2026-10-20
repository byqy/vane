package xyz.byqy.vane.api.admin;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.iam.application.DeptNode;
import xyz.byqy.vane.iam.application.DeptService;

/** 组织管理（ADM-03）。 */
@RestController
@RequestMapping("/api/v1/admin/depts")
public class AdminDeptController {

    public record CreateDeptRequest(UUID parentId, @NotBlank @Size(max = 64) String code,
                                    @NotBlank @Size(max = 64) String name, Integer sortNo) {
    }

    public record UpdateDeptRequest(UUID parentId, @Size(max = 64) String name,
                                    @jakarta.validation.constraints.Pattern(regexp = "ACTIVE|DISABLED") String status,
                                    Integer sortNo) {
    }

    private final DeptService depts;

    public AdminDeptController(DeptService depts) {
        this.depts = depts;
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DEPT.code)")
    public List<DeptNode> tree() {
        return depts.tree();
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DEPT_CREATE.code)")
    @Audit(module = "dept", action = "create")
    public ResponseEntity<DeptNode> create(@Valid @RequestBody CreateDeptRequest request) {
        DeptNode created = depts.create(request.parentId(), request.code(), request.name(),
                request.sortNo() == null ? 0 : request.sortNo());
        return ResponseEntity.created(URI.create("/api/v1/admin/depts/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DEPT_UPDATE.code)")
    @Audit(module = "dept", action = "update")
    public DeptNode update(@PathVariable UUID id, @Valid @RequestBody UpdateDeptRequest request) {
        return depts.update(id, request.parentId(), request.name(), request.status(),
                request.sortNo() == null ? 0 : request.sortNo());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DEPT_DELETE.code)")
    @Audit(module = "dept", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        depts.delete(id);
        return ResponseEntity.noContent().build();
    }
}
