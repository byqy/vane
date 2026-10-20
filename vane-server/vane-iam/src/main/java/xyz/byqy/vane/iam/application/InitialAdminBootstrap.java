package xyz.byqy.vane.iam.application;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初始管理员引导（05 §9.2）：库中不存在超级管理员时创建 admin。
 * 密码优先取配置 vane.iam.bootstrap-admin.password；未配置则随机生成，只在启动日志输出一次。
 * 账号标记 must_change_password=true，首次登录后必须修改。
 */
@Component
public class InitialAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String configuredPassword;
    private final String deptCode;
    private final String deptName;
    private final String username;

    public InitialAdminBootstrap(JdbcTemplate jdbc,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${vane.iam.bootstrap-admin.enabled:true}") boolean enabled,
                                 @Value("${vane.iam.bootstrap-admin.password:}") String configuredPassword,
                                 @Value("${vane.iam.bootstrap-admin.dept-code:HQ}") String deptCode,
                                 @Value("${vane.iam.bootstrap-admin.dept-name:总部}") String deptName,
                                 @Value("${vane.iam.bootstrap-admin.username:admin}") String username) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.configuredPassword = configuredPassword;
        this.deptCode = deptCode;
        this.deptName = deptName;
        this.username = username;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        Integer superAdmins = jdbc.queryForObject("""
                select count(*)
                  from iam_user_role ur
                  join iam_role r on r.id = ur.role_id
                  join iam_user u on u.id = ur.user_id
                 where r.code = 'SUPER_ADMIN' and u.deleted_at is null
                """, Integer.class);
        if (superAdmins != null && superAdmins > 0) {
            return;
        }

        UUID deptId = ensureRootDept();
        String rawPassword = configuredPassword == null || configuredPassword.isBlank()
                ? randomPassword()
                : configuredPassword;
        UUID userId = ensureAdminUser(deptId, rawPassword);
        ensureSuperAdminRole(userId);

        if (configuredPassword == null || configuredPassword.isBlank()) {
            log.warn("初始管理员已创建：username={} password={}（仅显示一次，首次登录后必须修改）", username, rawPassword);
        } else {
            log.warn("初始管理员已创建：username={}（密码来自配置 vane.iam.bootstrap-admin.password）", username);
        }
    }

    private UUID ensureRootDept() {
        List<UUID> existing = jdbc.query("""
                select id from iam_dept where code = ? and deleted_at is null
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), deptCode);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into iam_dept (id, parent_id, code, name, sort_no, status)
                values (?, null, ?, ?, 10, 'ACTIVE')
                """, id, deptCode, deptName);
        return id;
    }

    private UUID ensureAdminUser(UUID deptId, String rawPassword) {
        List<UUID> existing = jdbc.query("""
                select id from iam_user where username = ? and deleted_at is null
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), username);
        String hash = passwordEncoder.encode(rawPassword);
        Instant now = Instant.now();
        if (!existing.isEmpty()) {
            UUID id = existing.get(0);
            jdbc.update("""
                    update iam_user
                       set password_hash = ?, dept_id = ?, status = 'ACTIVE', mfa_enabled = false,
                           locked_until = null, must_change_password = true, password_updated_at = ?,
                           updated_at = ?
                     where id = ?
                    """, hash, deptId, Timestamp.from(now), Timestamp.from(now), id);
            return id;
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into iam_user (id, username, password_hash, nickname, dept_id, status,
                                      mfa_enabled, must_change_password, password_updated_at, locale, timezone)
                values (?, ?, ?, ?, ?, 'ACTIVE', false, true, ?, 'zh-CN', 'Asia/Shanghai')
                """, id, username, hash, "管理员", deptId, Timestamp.from(now));
        return id;
    }

    private void ensureSuperAdminRole(UUID userId) {
        UUID roleId = jdbc.queryForObject("select id from iam_role where code = 'SUPER_ADMIN'",
                (rs, rowNum) -> rs.getObject("id", UUID.class));
        Integer exists = jdbc.queryForObject(
                "select count(*) from iam_user_role where user_id = ? and role_id = ?",
                Integer.class, userId, roleId);
        if (exists == null || exists == 0) {
            jdbc.update("insert into iam_user_role (user_id, role_id) values (?, ?)", userId, roleId);
        }
    }

    private String randomPassword() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
