package xyz.byqy.vane.api.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.system.application.SystemHealthService;

/** 健康与指标（ADM-12）：需 ops:health 权限。 */
@RestController
@RequestMapping("/api/v1/admin/health")
public class AdminHealthController {

    private final SystemHealthService health;

    public AdminHealthController(SystemHealthService health) {
        this.health = health;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_HEALTH.code)")
    public SystemHealthService.HealthView health() {
        return health.check();
    }
}
