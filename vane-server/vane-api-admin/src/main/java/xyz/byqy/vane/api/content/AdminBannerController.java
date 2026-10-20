package xyz.byqy.vane.api.content;

import java.net.URI;
import java.time.Instant;
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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.content.application.BannerService;
import xyz.byqy.vane.content.application.BannerView;

/** 轮播 / 宣传位管理（ADM-08）。 */
@RestController
@RequestMapping("/api/v1/admin/banners")
public class AdminBannerController {

    public record BannerRequest(@NotBlank String position, @NotNull UUID imageFileId, String titleKey,
                                String linkUrl, Integer sortNo, Instant startAt, Instant endAt) {
    }

    public record BannerUpdateRequest(@NotNull UUID imageFileId, String titleKey, String linkUrl, Integer sortNo,
                                      @Pattern(regexp = "ACTIVE|DISABLED") String status,
                                      Instant startAt, Instant endAt) {
    }

    private final BannerService banners;

    public AdminBannerController(BannerService banners) {
        this.banners = banners;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_BANNER.code)")
    public List<BannerView> list(@RequestParam(required = false) String position) {
        return banners.list(position, false);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_BANNER_CREATE.code)")
    @Audit(module = "banner", action = "create")
    public ResponseEntity<BannerView> create(@Valid @RequestBody BannerRequest request) {
        BannerView created = banners.create(request.position(), request.imageFileId(), request.titleKey(),
                request.linkUrl(), request.sortNo() == null ? 0 : request.sortNo(), request.startAt(), request.endAt());
        return ResponseEntity.created(URI.create("/api/v1/admin/banners/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_BANNER_UPDATE.code)")
    @Audit(module = "banner", action = "update")
    public BannerView update(@PathVariable UUID id, @Valid @RequestBody BannerUpdateRequest request) {
        return banners.update(id, request.imageFileId(), request.titleKey(), request.linkUrl(),
                request.sortNo() == null ? 0 : request.sortNo(), request.status(), request.startAt(), request.endAt());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_BANNER_DELETE.code)")
    @Audit(module = "banner", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        banners.delete(id);
        return ResponseEntity.noContent().build();
    }
}
