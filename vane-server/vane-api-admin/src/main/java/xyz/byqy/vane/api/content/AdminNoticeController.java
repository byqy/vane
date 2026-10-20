package xyz.byqy.vane.api.content;

import java.net.URI;
import java.time.Instant;
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
import xyz.byqy.vane.content.application.NoticeService;
import xyz.byqy.vane.content.application.NoticeTranslation;
import xyz.byqy.vane.content.application.NoticeView;
import xyz.byqy.vane.content.application.PageResult;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;

/** 公告管理（ADM-08）。 */
@RestController
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {

    public record TranslationRequest(@NotBlank String locale, @NotBlank @Size(max = 200) String title,
                                     String body) {
    }

    public record NoticeRequest(
            @NotBlank @Pattern(regexp = "INFO|WARN|URGENT") String level,
            boolean pinned,
            Instant publishAt,
            Instant expireAt,
            @NotEmpty List<@Valid TranslationRequest> translations) {
    }

    private final NoticeService notices;

    public AdminNoticeController(NoticeService notices) {
        this.notices = notices;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_NOTICE.code)")
    public PageResult<NoticeView> list(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return notices.adminPage(pageNumber, pageSize);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_NOTICE_CREATE.code)")
    @Audit(module = "notice", action = "create")
    public ResponseEntity<NoticeView> create(@Valid @RequestBody NoticeRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser actor) {
        NoticeView created = notices.create(request.level(), request.pinned(), request.publishAt(),
                request.expireAt(), toTranslations(request.translations()), actor == null ? null : actor.id());
        return ResponseEntity.created(URI.create("/api/v1/admin/notices/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_NOTICE_UPDATE.code)")
    @Audit(module = "notice", action = "update")
    public NoticeView update(@PathVariable UUID id, @Valid @RequestBody NoticeRequest request) {
        return notices.update(id, request.level(), request.pinned(), request.publishAt(),
                request.expireAt(), toTranslations(request.translations()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_NOTICE_PUBLISH.code)")
    @Audit(module = "notice", action = "publish")
    public NoticeView publish(@PathVariable UUID id) {
        return notices.publish(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_NOTICE_DELETE.code)")
    @Audit(module = "notice", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        notices.delete(id);
        return ResponseEntity.noContent().build();
    }

    private List<NoticeTranslation> toTranslations(List<TranslationRequest> requests) {
        return requests.stream()
                .map(t -> new NoticeTranslation(t.locale(), t.title(), t.body()))
                .toList();
    }
}
