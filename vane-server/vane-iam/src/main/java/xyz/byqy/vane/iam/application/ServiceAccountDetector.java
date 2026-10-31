package xyz.byqy.vane.iam.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.iam.domain.IamRoleRepository;

/** 判断账号是否为服务账号（持有服务角色），用于对高危操作施加额外约束（ADR-030 P1/P2）。 */
@Service
public class ServiceAccountDetector {

    private final IamRoleRepository roles;

    public ServiceAccountDetector(IamRoleRepository roles) {
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public boolean isServiceAccount(UUID userId) {
        return userId != null && roles.hasServiceRole(userId);
    }
}
