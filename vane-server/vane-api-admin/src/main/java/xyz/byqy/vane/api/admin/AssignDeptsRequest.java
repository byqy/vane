package xyz.byqy.vane.api.admin;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AssignDeptsRequest(@NotNull List<UUID> deptIds) {
}
