package xyz.byqy.vane.api.content;

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.content.application.ContentPageService;
import xyz.byqy.vane.content.application.PageResult;
import xyz.byqy.vane.content.application.PageTranslation;
import xyz.byqy.vane.content.application.PageView;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;

/** 内容页管理（ADM-08）。 */
@RestController
@RequestMapping("/api/v1/admin/pages")
public class AdminPageController {

    public record TranslationRequest(@NotBlank String locale, @NotBlank @Size(max = 200) String title,
                                     String summary, String body) {
    }

    public record PageRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String slug,
            UUID coverFileId,
            @NotEmpty List<@Valid TranslationRequest> translations) {
    }

    public record PageUpdateRequest(UUID coverFileId, @NotEmpty List<@Valid TranslationRequest> translations) {
    }

    private final ContentPageService pages;

    public AdminPageController(ContentPageService pages) {
        this.pages = pages;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PAGE.code)")
    public PageResult<PageView> list(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return pages.adminPage(pageNumber, pageSize);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PAGE_CREATE.code)")
    @Audit(module = "page", action = "create")
    public ResponseEntity<PageView> create(@Valid @RequestBody PageRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        PageView created = pages.create(request.slug(), request.coverFileId(),
                toTranslations(request.translations()), actor == null ? null : actor.id());
        return ResponseEntity.created(URI.create("/api/v1/admin/pages/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PAGE_UPDATE.code)")
    @Audit(module = "page", action = "update")
    public PageView update(@PathVariable UUID id, @Valid @RequestBody PageUpdateRequest request) {
        return pages.update(id, request.coverFileId(), toTranslations(request.translations()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PAGE_PUBLISH.code)")
    @Audit(module = "page", action = "publish")
    public PageView publish(@PathVariable UUID id) {
        return pages.publish(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PAGE_DELETE.code)")
    @Audit(module = "page", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pages.delete(id);
        return ResponseEntity.noContent().build();
    }

    private List<PageTranslation> toTranslations(List<TranslationRequest> requests) {
        return requests.stream()
                .map(t -> new PageTranslation(t.locale(), t.title(), t.summary(), t.body()))
                .toList();
    }
}
