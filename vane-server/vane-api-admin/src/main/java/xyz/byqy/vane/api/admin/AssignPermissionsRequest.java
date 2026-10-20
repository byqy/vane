package xyz.byqy.vane.api.admin;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public record AssignPermissionsRequest(@NotNull List<String> permissionCodes) {
}
