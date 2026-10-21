package xyz.byqy.vane.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditContextTest {

    @AfterEach
    void clear() {
        AuditContext.clear();
    }

    @Test
    void recordsAreCollectedUntilContextIsCleared() {
        AuditContext context = AuditContext.begin();
        AuditContext.record(Map.of("a", 1), Map.of("a", 2), "a");
        AuditContext.setAffectedCount(7);

        assertThat(AuditContext.current()).isSameAs(context);
        assertThat(context.records()).hasSize(1);
        assertThat(context.affectedCount()).isEqualTo(7);

        AuditContext.clear();
        assertThat(AuditContext.current()).isNull();
    }

    @Test
    void recordingWithoutContextIsIgnored() {
        AuditContext.record(Map.of("a", 1), Map.of("a", 2), "a");
        AuditContext.setAffectedCount(3);

        assertThat(AuditContext.current()).isNull();
    }

    @Test
    void createdAndDeletedRecordsShareTheSameShape() {
        AuditContext context = AuditContext.begin();
        AuditContext.recordCreated(Map.of("a", 1), "a");
        AuditContext.recordDeleted(Map.of("b", 2), "b");

        assertThat(context.records()).hasSize(2);
        assertThat(context.records().get(0).before()).isNull();
        assertThat(context.records().get(1).after()).isNull();
    }
}
