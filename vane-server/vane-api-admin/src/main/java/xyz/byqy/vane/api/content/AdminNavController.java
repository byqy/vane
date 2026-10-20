package xyz.byqy.vane.api.content;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.content.application.NavService;
import xyz.byqy.vane.content.application.NavView;

/** 导航管理（ADM-08）。 */
@RestController
@RequestMapping("/api/v1/admin/navs")
public class AdminNavController {

    public record NavRequest(UUID parentId, @NotBlank @Pattern(regexp = "HEADER|FOOTER") String position,
                             @NotBlank String labelKey, String target, Boolean openInNewTab, Integer sortNo) {
    }

    public record NavUpdateRequest(UUID parentId, @NotBlank String labelKey, String target, Boolean openInNewTab,
                                   Integer sortNo, @Pattern(regexp = "ACTIVE|DISABLED") String status) {
    }

    private final NavService navs;

    public AdminNavController(NavService navs) {
        this.navs = navs;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_NAV.code)")
    public List<NavView> tree(@RequestParam(required = false) String position) {
        return navs.tree(position, false);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_NAV_CREATE.code)")
    @Audit(module = "nav", action = "create")
    public ResponseEntity<NavView> create(@Valid @RequestBody NavRequest request) {
        NavView created = navs.create(request.parentId(), request.position(), request.labelKey(), request.target(),
                Boolean.TRUE.equals(request.openInNewTab()), request.sortNo() == null ? 0 : request.sortNo());
        return ResponseEntity.created(URI.create("/api/v1/admin/navs/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_NAV_UPDATE.code)")
    @Audit(module = "nav", action = "update")
    public NavView update(@PathVariable UUID id, @Valid @RequestBody NavUpdateRequest request) {
        return navs.update(id, request.parentId(), request.labelKey(), request.target(),
                Boolean.TRUE.equals(request.openInNewTab()), request.sortNo() == null ? 0 : request.sortNo(),
                request.status());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_NAV_DELETE.code)")
    @Audit(module = "nav", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        navs.delete(id);
        return ResponseEntity.noContent().build();
    }
}
