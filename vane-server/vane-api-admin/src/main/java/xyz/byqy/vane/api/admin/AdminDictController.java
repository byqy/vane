package xyz.byqy.vane.api.admin;

import java.util.List;

import org.springframework.context.i18n.LocaleContextHolder;
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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;
import xyz.byqy.vane.system.application.DictItemView;
import xyz.byqy.vane.system.application.DictTypeView;
import xyz.byqy.vane.system.application.SysDictService;

/** 字典（ADM-06）：读多写少；标签按 Accept-Language 返回，写操作按权限点与审计记录。 */
@RestController
@RequestMapping("/api/v1/admin/dicts")
public class AdminDictController {

    public record TypeRequest(@NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{1,63}") String typeCode,
                              @NotBlank String nameKey, String remark) {
    }

    public record TypeUpdateRequest(@NotBlank String nameKey,
                                    @Pattern(regexp = "ACTIVE|DISABLED") String status, String remark) {
    }

    public record TranslationRequest(@NotBlank String locale, @NotBlank String label) {
    }

    public record ItemRequest(@NotBlank String value, String color, Integer sortNo,
                              List<@Valid TranslationRequest> translations) {
    }

    public record ItemUpdateRequest(String color, Integer sortNo,
                                    @Pattern(regexp = "ACTIVE|DISABLED") String status,
                                    List<@Valid TranslationRequest> translations) {
    }

    private final SysDictService dicts;

    public AdminDictController(SysDictService dicts) {
        this.dicts = dicts;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DICT.code)")
    public List<DictTypeView> types() {
        return dicts.types();
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DICT_CREATE.code)")
    @Audit(module = "dict", action = "create-type")
    public ResponseEntity<Void> createType(@Valid @RequestBody TypeRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        dicts.createType(request.typeCode(), request.nameKey(), request.remark(), actor == null ? null : actor.id());
        return ResponseEntity.status(201).build();
    }

    @PatchMapping("/{code}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DICT_UPDATE.code)")
    @Audit(module = "dict", action = "update-type")
    public ResponseEntity<Void> updateType(@PathVariable String code,
                                           @Valid @RequestBody TypeUpdateRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        dicts.updateType(code, request.nameKey(), request.status(), request.remark(), actor == null ? null : actor.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{code}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DICT_DELETE.code)")
    @Audit(module = "dict", action = "delete-type")
    public ResponseEntity<Void> deleteType(@PathVariable String code,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        dicts.deleteType(code, actor == null ? null : actor.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{code}/items")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DICT.code)")
    public List<DictItemView> items(@PathVariable String code) {
        return dicts.items(code, LocaleContextHolder.getLocale().toLanguageTag());
    }

    @PostMapping("/{code}/items")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DICT_CREATE.code)")
    @Audit(module = "dict", action = "create-item")
    public ResponseEntity<Void> createItem(@PathVariable String code,
                                           @Valid @RequestBody ItemRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        dicts.createItem(code, request.value(), request.color(),
                request.sortNo() == null ? 0 : request.sortNo(), toTranslations(request.translations()),
                actor == null ? null : actor.id());
        return ResponseEntity.status(201).build();
    }

    @PatchMapping("/{code}/items/{value}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DICT_UPDATE.code)")
    @Audit(module = "dict", action = "update-item")
    public ResponseEntity<Void> updateItem(@PathVariable String code, @PathVariable String value,
                                           @Valid @RequestBody ItemUpdateRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        dicts.updateItem(code, value, request.color(), request.sortNo() == null ? 0 : request.sortNo(),
                request.status(), toTranslations(request.translations()), actor == null ? null : actor.id());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{code}/items/{value}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_DICT_DELETE.code)")
    @Audit(module = "dict", action = "delete-item")
    public ResponseEntity<Void> deleteItem(@PathVariable String code, @PathVariable String value,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        dicts.deleteItem(code, value, actor == null ? null : actor.id());
        return ResponseEntity.noContent().build();
    }

    private List<SysDictService.Translation> toTranslations(List<TranslationRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(item -> new SysDictService.Translation(item.locale(), item.label()))
                .toList();
    }
}




