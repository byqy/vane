package xyz.byqy.vane.api.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateRoleRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Pattern(regexp = "ALL|DEPT_AND_CHILD|DEPT|SELF|CUSTOM") String dataScope) {
}
