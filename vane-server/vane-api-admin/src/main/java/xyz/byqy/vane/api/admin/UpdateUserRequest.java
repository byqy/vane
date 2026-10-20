package xyz.byqy.vane.api.admin;

import java.util.UUID;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 64) String nickname,
        UUID deptId,
        @Pattern(regexp = "ACTIVE|DISABLED") String status) {
}
