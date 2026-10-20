package xyz.byqy.vane.file.application;

import java.util.UUID;

public record FileDownload(UUID id, String filename, String mime, byte[] content) {
}
