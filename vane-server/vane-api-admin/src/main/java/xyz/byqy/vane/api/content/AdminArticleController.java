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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.content.application.ArticleService;
import xyz.byqy.vane.content.application.ArticleTranslation;
import xyz.byqy.vane.content.application.ArticleView;
import xyz.byqy.vane.content.application.PageResult;
import xyz.byqy.vane.content.application.TagView;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;

/** 文章管理（ADM-08）：多语言正文、发布/下线、标签整体替换。 */
@RestController
@RequestMapping("/api/v1/admin/articles")
public class AdminArticleController {

    public record TranslationRequest(@NotBlank String locale, @NotBlank @Size(max = 200) String title,
                                     String summary, String body) {
    }

    public record ArticleRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String slug,
            UUID categoryId,
            UUID coverFileId,
            @NotEmpty List<@Valid TranslationRequest> translations) {
    }

    public record TagsRequest(@NotNull List<UUID> tagIds) {
    }

    private final ArticleService articles;

    public AdminArticleController(ArticleService articles) {
        this.articles = articles;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_ARTICLE.code)")
    public PageResult<ArticleView> list(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String keyword) {
        return articles.adminPage(pageNumber, pageSize, status, categoryId, keyword);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_ARTICLE_CREATE.code)")
    @Audit(module = "article", action = "create")
    public ResponseEntity<ArticleView> create(@Valid @RequestBody ArticleRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        ArticleView created = articles.create(request.slug(), request.categoryId(), request.coverFileId(),
                toTranslations(request.translations()), actor == null ? null : actor.id());
        return ResponseEntity.created(URI.create("/api/v1/admin/articles/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_ARTICLE_UPDATE.code)")
    @Audit(module = "article", action = "update")
    public ArticleView update(@PathVariable UUID id, @Valid @RequestBody ArticleRequest request) {
        return articles.update(id, request.slug(), request.categoryId(), request.coverFileId(),
                toTranslations(request.translations()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_ARTICLE_PUBLISH.code)")
    @Audit(module = "article", action = "publish")
    public ArticleView publish(@PathVariable UUID id) {
        return articles.publish(id);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_ARTICLE_PUBLISH.code)")
    @Audit(module = "article", action = "unpublish")
    public ArticleView unpublish(@PathVariable UUID id) {
        return articles.unpublish(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_ARTICLE_DELETE.code)")
    @Audit(module = "article", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        articles.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/tags")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_ARTICLE.code)")
    public List<TagView> tags(@PathVariable UUID id) {
        return articles.tags(id);
    }

    @PutMapping("/{id}/tags")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_ARTICLE_UPDATE.code)")
    @Audit(module = "article", action = "update-tags")
    public ResponseEntity<Void> replaceTags(@PathVariable UUID id, @Valid @RequestBody TagsRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser actor) {
        articles.replaceTags(id, request.tagIds(), actor == null ? null : actor.id());
        return ResponseEntity.noContent().build();
    }

    private List<ArticleTranslation> toTranslations(List<TranslationRequest> requests) {
        return requests.stream()
                .map(t -> new ArticleTranslation(t.locale(), t.title(), t.summary(), t.body()))
                .toList();
    }
}
