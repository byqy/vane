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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.content.application.CategoryNodeView;
import xyz.byqy.vane.content.application.CategoryService;
import xyz.byqy.vane.content.application.CategoryTranslation;
import xyz.byqy.vane.content.application.CategoryView;

/** 文章分类管理（ADM-08）：业务上只有一级分类，不提供多级树操作。 */
@RestController
@RequestMapping("/api/v1/admin/categories")
public class AdminCategoryController {

    public record TranslationRequest(@NotBlank String locale, @NotBlank @Size(max = 100) String name,
                                     String description) {
    }

    public record CategoryRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String code,
            Integer sortNo,
            @Pattern(regexp = "ACTIVE|DISABLED") String status,
            @NotEmpty List<@Valid TranslationRequest> translations) {
    }

    private final CategoryService categories;

    public AdminCategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_CATEGORY.code)")
    public List<CategoryNodeView> tree() {
        return categories.adminTree();
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_CATEGORY_CREATE.code)")
    @Audit(module = "category", action = "create")
    public ResponseEntity<CategoryView> create(@Valid @RequestBody CategoryRequest request) {
        CategoryView created = categories.create(request.code(), request.sortNo() == null ? 0 : request.sortNo(),
                request.status(), toTranslations(request.translations()));
        return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_CATEGORY_UPDATE.code)")
    @Audit(module = "category", action = "update")
    public CategoryView update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return categories.update(id, request.code(), request.sortNo() == null ? 0 : request.sortNo(),
                request.status(), toTranslations(request.translations()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_CATEGORY_DELETE.code)")
    @Audit(module = "category", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categories.delete(id);
        return ResponseEntity.noContent().build();
    }

    private List<CategoryTranslation> toTranslations(List<TranslationRequest> requests) {
        return requests.stream()
                .map(t -> new CategoryTranslation(t.locale(), t.name(), t.description()))
                .toList();
    }
}
