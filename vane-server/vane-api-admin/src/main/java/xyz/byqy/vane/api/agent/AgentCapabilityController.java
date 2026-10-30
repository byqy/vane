package xyz.byqy.vane.api.agent;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 能力清单端点（ADR-030 能力层）。
 *
 * <p>需认证后访问；内容由运行时元数据生成，与实现同源。</p>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentCapabilityController {

    private final CapabilityManifestBuilder builder;

    public AgentCapabilityController(CapabilityManifestBuilder builder) {
        this.builder = builder;
    }

    @GetMapping("/capabilities")
    @PreAuthorize("isAuthenticated()")
    public CapabilityManifest capabilities() {
        return builder.build();
    }
}
