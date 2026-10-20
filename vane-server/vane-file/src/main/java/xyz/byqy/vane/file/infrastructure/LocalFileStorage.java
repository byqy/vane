package xyz.byqy.vane.file.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import xyz.byqy.vane.file.application.FileStorage;

/** 本地磁盘实现：所有对象落在同一根目录下，按 objectKey 分片。 */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(@Value("${vane.file.local-root:./data/files}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    @Override
    public void put(String objectKey, byte[] content) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    @Override
    public byte[] get(String objectKey) throws IOException {
        return Files.readAllBytes(resolve(objectKey));
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(objectKey));
    }

    @Override
    public String name() {
        return "LOCAL";
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid object key");
        }
        return target;
    }
}
