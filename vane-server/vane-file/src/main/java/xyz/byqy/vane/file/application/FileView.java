package xyz.byqy.vane.file.application;

import java.util.UUID;

public record FileView(UUID id, String name, String mime, long size, String visibility) {
}
