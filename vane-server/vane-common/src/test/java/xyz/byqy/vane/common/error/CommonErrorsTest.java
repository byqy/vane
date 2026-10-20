package xyz.byqy.vane.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CommonErrorsTest {

    @Test
    void codesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (CommonErrors value : CommonErrors.values()) {
            assertThat(seen.add(value.code())).as(value.code()).isTrue();
        }
    }

    @Test
    void everyCodeHasStatusAndProblemType() {
        for (CommonErrors value : CommonErrors.values()) {
            assertThat(value.status()).as(value.code()).isNotNull();
            assertThat(value.type().uri()).as(value.code()).startsWith("/problems/");
            assertThat(value.detailKey())
                    .as(value.code())
                    .isEqualTo("error." + value.code().toLowerCase(Locale.ROOT).replace('_', '-'));
        }
    }

    @Test
    void registryKnowsEveryCommonCode() {
        for (CommonErrors value : CommonErrors.values()) {
            assertThat(ErrorCodeRegistry.find(value.code())).as(value.code()).contains(value);
        }
    }

    @Test
    void codeFormatIsDomainDotResourceDotReason() {
        for (CommonErrors value : CommonErrors.values()) {
            assertThat(value.code()).as(value.code()).matches("^[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)+$");
        }
    }
}
