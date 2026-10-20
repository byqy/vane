package xyz.byqy.vane.file.application;

import java.io.IOException;

/** 存储抽象（ADR-011）：默认本地磁盘，可切换 S3 兼容实现。 */
public interface FileStorage {

    void put(String objectKey, byte[] content) throws IOException;

    byte[] get(String objectKey) throws IOException;

    void delete(String objectKey) throws IOException;

    String name();
}
