package xyz.byqy.vane.api.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateRoleRequest(
        @Size(max = 64) String name,
        @Pattern(regexp = "ALL|DEPT_AND_CHILD|DEPT|SELF|CUSTOM") String dataScope,
        @Pattern(regexp = "ACTIVE|DISABLED") String status,
        Integer sortNo,
        Boolean service) {
}
