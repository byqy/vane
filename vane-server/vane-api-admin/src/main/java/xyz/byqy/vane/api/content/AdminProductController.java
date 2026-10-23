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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.content.application.PageResult;
import xyz.byqy.vane.content.application.ProductCommand;
import xyz.byqy.vane.content.application.ProductDetailView;
import xyz.byqy.vane.content.application.ProductFeature;
import xyz.byqy.vane.content.application.ProductService;
import xyz.byqy.vane.content.application.ProductTranslation;
import xyz.byqy.vane.content.application.ProductView;
import xyz.byqy.vane.content.application.PublicProductMedia;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;

/** 产品管理（ADM-08 / ADR-025）：多语言内容、特性、媒体集与上下线。 */
@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {

    public record FeatureRequest(@NotBlank @Size(max = 200) String title, String description) {
    }

    public record TranslationRequest(@NotBlank String locale, @NotBlank @Size(max = 200) String name,
                                     String summary, String body, List<@Valid FeatureRequest> features) {
    }

    public record MediaRequest(@NotBlank String fileId, @NotBlank @Pattern(regexp = "IMAGE|VIDEO") String kind,
                               Integer sortNo, String caption) {
    }

    public record ProductRequest(
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String code,
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,63}") String slug,
            @Size(max = 64) String group,
            String coverFileId,
            String videoFileId,
            @Pattern(regexp = "DRAFT|PUBLISHED|ARCHIVED") String status,
            Integer sortNo,
            @NotEmpty List<@Valid TranslationRequest> translations,
            List<@Valid MediaRequest> media) {
    }

    public record MediaSetRequest(List<@Valid MediaRequest> media) {
    }

    private final ProductService products;

    public AdminProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PRODUCT.code)")
    public PageResult<ProductView> list(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return products.adminPage(pageNumber, pageSize, status, keyword);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PRODUCT.code)")
    public ProductDetailView get(@PathVariable UUID id) {
        return products.adminGet(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PRODUCT.code)")
    @Audit(module = "product", action = "create")
    public ResponseEntity<ProductView> create(@Valid @RequestBody ProductRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser actor) {
        ProductView created = products.create(command(request), actor == null ? null : actor.id());
        return ResponseEntity.created(URI.create("/api/v1/admin/products/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PRODUCT.code)")
    @Audit(module = "product", action = "update")
    public ProductView update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, command(request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PRODUCT.code)")
    @Audit(module = "product", action = "publish")
    public ProductView publish(@PathVariable UUID id) {
        return products.publish(id);
    }

    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PRODUCT.code)")
    @Audit(module = "product", action = "unpublish")
    public ProductView unpublish(@PathVariable UUID id) {
        return products.unpublish(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PRODUCT.code)")
    @Audit(module = "product", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        products.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/media")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).CONTENT_PRODUCT_MEDIA.code)")
    @Audit(module = "product", action = "update-media")
    public ResponseEntity<Void> replaceMedia(@PathVariable UUID id, @Valid @RequestBody MediaSetRequest request) {
        products.replaceMedia(id, toMedia(request.media()));
        return ResponseEntity.noContent().build();
    }

    private ProductCommand command(ProductRequest request) {
        return new ProductCommand(request.code(), request.slug(), request.group(), request.coverFileId(),
                request.videoFileId(), request.status(), request.sortNo() == null ? 0 : request.sortNo(),
                toTranslations(request.translations()), toMedia(request.media()));
    }

    private List<ProductTranslation> toTranslations(List<TranslationRequest> requests) {
        return requests.stream()
                .map(item -> new ProductTranslation(item.locale(), item.name(), item.summary(), item.body(),
                        item.features() == null ? List.of() : item.features().stream()
                                .map(feature -> new ProductFeature(feature.title(), feature.description()))
                                .toList()))
                .toList();
    }

    private List<PublicProductMedia> toMedia(List<MediaRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(item -> new PublicProductMedia(item.fileId(), item.kind(),
                        item.sortNo() == null ? 0 : item.sortNo(), item.caption()))
                .toList();
    }
}
