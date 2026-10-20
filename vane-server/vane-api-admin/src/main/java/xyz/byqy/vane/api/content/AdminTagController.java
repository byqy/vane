package xyz.byqy.vane.api.content;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.content.application.TagService;
import xyz.byqy.vane.content.application.TagView;

/** 文章标签管理（ADM-08）。 */
@RestController
@RequestMapping("/api/v1/admin/tags")
public class AdminTagController {

    public record TagRequest(@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String code) {
    }

    private final TagService tags;

    public AdminTagController(TagService tags) {
        this.tags = tags;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_TAG.code)")
    public List<TagView> list() {
        return tags.adminList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_TAG_CREATE.code)")
    @Audit(module = "tag", action = "create")
    public ResponseEntity<TagView> create(@Valid @RequestBody TagRequest request) {
        TagView created = tags.create(request.code());
        return ResponseEntity.created(URI.create("/api/v1/admin/tags/" + created.id())).body(created);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_TAG_DELETE.code)")
    @Audit(module = "tag", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        tags.delete(id);
        return ResponseEntity.noContent().build();
    }
}
