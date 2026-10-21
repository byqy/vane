package xyz.byqy.vane.audit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import xyz.byqy.vane.common.audit.AuditContext;

class AuditDiffTest {

    private final AuditDiff diff = new AuditDiff(JsonMapper.builder().build(), new AuditMasker(JsonMapper.builder().build()));

    @BeforeEach
    void open() {
        AuditContext.begin();
    }

    @AfterEach
    void close() {
        AuditContext.clear();
    }

    @Test
    void onlyWhitelistedFieldsAreReported() {
        AuditContext.record(Map.of("nickname", "a", "status", "ACTIVE"),
                Map.of("nickname", "b", "status", "DISABLED"), "nickname");

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.changesJson()).contains("\"field\":\"nickname\"")
                .contains("\"before\":\"a\"").contains("\"after\":\"b\"")
                .doesNotContain("status");
    }

    @Test
    void unchangedFieldsAreOmittedAndEmptyArrayMeansNoChange() {
        AuditContext.record(Map.of("status", "ACTIVE"), Map.of("status", "ACTIVE"), "status");

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.changesJson()).isEqualTo("[]");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void createdRecordHasNoBeforeValue() {
        AuditContext.recordCreated(Map.of("username", "bob"), "username");

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.changesJson()).contains("\"field\":\"username\"").contains("\"after\":\"bob\"");
    }

    @Test
    void deletedRecordHasNoAfterValue() {
        AuditContext.recordDeleted(Map.of("username", "bob"), "username");

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.changesJson()).contains("\"field\":\"username\"").contains("\"before\":\"bob\"");
    }

    @Test
    void nestedPathsAreSupported() {
        AuditContext.record(Map.of("profile", Map.of("nickname", "a")),
                Map.of("profile", Map.of("nickname", "b")), "profile.nickname");

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.changesJson()).contains("\"field\":\"profile.nickname\"")
                .contains("\"before\":\"a\"").contains("\"after\":\"b\"");
    }

    @Test
    void sensitiveFieldsAreAlwaysMasked() {
        AuditContext.record(Map.of("password", "old-secret"), Map.of("password", "new-secret"), "password");

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.changesJson()).doesNotContain("old-secret").doesNotContain("new-secret")
                .contains("\"***\"");
    }

    @Test
    void listValuesAreComparedAsWhole() {
        AuditContext.record(Map.of("permissionCodes", List.of("a", "b")),
                Map.of("permissionCodes", List.of("a", "c")), "permissionCodes");

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.changesJson()).contains("permissionCodes").contains("\"b\"").contains("\"c\"");
    }

    @Test
    void moreThanHundredFieldsAreTruncated() {
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        String[] fields = new String[120];
        for (int i = 0; i < fields.length; i++) {
            fields[i] = "f" + i;
            before.put(fields[i], "old");
            after.put(fields[i], "new");
        }
        AuditContext.record(before, after, fields);

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.truncated()).isTrue();
    }

    @Test
    void missingContextYieldsNoChanges() {
        assertThat(diff.diff(null)).isNull();
    }

    @Test
    void withoutWhitelistAllLeafFieldsAreCompared() {
        AuditContext.record(Map.of("nickname", "a", "status", "ACTIVE"),
                Map.of("nickname", "b", "status", "ACTIVE"));

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.changesJson()).contains("nickname").doesNotContain("status");
    }

    @Test
    void oversizedChangesAreTruncatedBySize() {
        String huge = "x".repeat(20_000);
        AuditContext.record(Map.of("payload", "small"), Map.of("payload", huge), "payload");

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.truncated()).isTrue();
        assertThat(result.changesJson().length()).isLessThan(20_000);
    }

    @Test
    void fieldPresentOnlyInAfterIsReportedWithNullBefore() {
        AuditContext.record(Map.of(), Map.of("deptId", "d1"), "deptId");

        AuditDiff.Result result = diff.diff(AuditContext.current());

        assertThat(result.changesJson()).contains("deptId").contains("\"before\":null");
    }
}
