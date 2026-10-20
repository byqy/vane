package xyz.byqy.vane.iam.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.iam.domain.IamUser;

/** 把 IamUser 组装成视图：批量补齐组织名与角色码，避免 N+1。 */
@Component
public class UserSummaryAssembler {

    private final JdbcTemplate jdbc;

    public UserSummaryAssembler(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserSummary> assemble(List<IamUser> users) {
        if (users.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = users.stream().map(IamUser::getId).toList();
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        Object[] args = ids.toArray();

        Map<UUID, String> deptNames = new HashMap<>();
        List<UUID> deptIds = users.stream().map(IamUser::getDeptId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        if (!deptIds.isEmpty()) {
            String deptPlaceholders = String.join(",", deptIds.stream().map(id -> "?").toList());
            String deptSql = "select id, name from iam_dept where id in (" + deptPlaceholders + ")";
            for (Map<String, Object> row : jdbc.queryForList(deptSql, deptIds.toArray())) {
                deptNames.put((UUID) row.get("id"), (String) row.get("name"));
            }
        }

        Map<UUID, List<String>> roles = new LinkedHashMap<>();
        String roleSql = "select ur.user_id as user_id, r.code as code from iam_user_role ur "
                + "join iam_role r on r.id = ur.role_id "
                + "where ur.user_id in (" + placeholders + ") order by r.sort_no";
        for (Map<String, Object> row : jdbc.queryForList(roleSql, args)) {
            roles.computeIfAbsent((UUID) row.get("user_id"), key -> new ArrayList<>())
                    .add((String) row.get("code"));
        }

        return users.stream()
                .map(u -> new UserSummary(u.getId(), u.getUsername(), u.getNickname(), u.getDeptId(),
                        deptNames.get(u.getDeptId()), u.getStatus(),
                        roles.getOrDefault(u.getId(), List.of()), u.getCreatedAt(), u.getLastLoginAt(),
                        u.getVersion()))
                .toList();
    }

    public UserSummary assemble(IamUser user) {
        return assemble(List.of(user)).get(0);
    }
}


