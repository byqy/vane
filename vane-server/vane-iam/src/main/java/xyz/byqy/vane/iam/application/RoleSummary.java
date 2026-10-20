package xyz.byqy.vane.iam.application;

import java.util.UUID;

public record RoleSummary(UUID id, String code, String name, String dataScope,
                          boolean builtin, String status, int sortNo) {
}
