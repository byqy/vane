package xyz.byqy.vane.api.admin;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;
import xyz.byqy.vane.system.application.ConfigView;
import xyz.byqy.vane.system.application.SysConfigService;

/** 系统参数（ADM-06）。 */
@RestController
@RequestMapping("/api/v1/admin/configs")
public class AdminConfigController {

    public record UpdateConfigRequest(@NotBlank String valueJson) {
    }

    private final SysConfigService configs;

    public AdminConfigController(SysConfigService configs) {
        this.configs = configs;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_CONFIG.code)")
    public List<ConfigView> list() {
        return configs.list();
    }

    @PatchMapping("/{key}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).SYSTEM_CONFIG_UPDATE.code)")
    @Audit(module = "config", action = "update")
    public ConfigView update(@PathVariable String key, @Valid @RequestBody UpdateConfigRequest request,
                             @AuthenticationPrincipal AuthenticatedUser actor) {
        return configs.update(key, request.valueJson(), actor == null ? null : actor.id());
    }
}


