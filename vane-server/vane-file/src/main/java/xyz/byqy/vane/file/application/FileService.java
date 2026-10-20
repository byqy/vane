package xyz.byqy.vane.file.application;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.file.domain.SysFile;
import xyz.byqy.vane.file.domain.SysFileRepository;

/**
 * 附件服务（03 §15）：
 * - 类型按魔数识别，不信任客户端声明
 * - 同名内容按 SHA-256 去重，复用已有对象
 * - 对外只暴露文件 id，不暴露存储路径
 */
@Service
public class FileService {

    private static final Set<String> ALLOWED = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf");

    private final SysFileRepository files;
    private final FileStorage storage;
    private final long maxSizeBytes;

    public FileService(SysFileRepository files, FileStorage storage,
                       @Value("${vane.file.max-size-mb:20}") long maxSizeMb) {
        this.files = files;
        this.storage = storage;
        this.maxSizeBytes = maxSizeMb * 1024 * 1024;
    }

    @Transactional
    public FileView upload(MultipartFile upload, String visibility, UUID ownerId) {
        if (upload == null || upload.isEmpty()) {
            throw new ApiException(CommonErrors.FILE_TYPE_NOT_ALLOWED);
        }
        if (upload.getSize() > maxSizeBytes) {
            throw new ApiException(CommonErrors.FILE_TOO_LARGE);
        }
        byte[] content;
        try {
            content = upload.getBytes();
        } catch (IOException ex) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED);
        }

        String mime = FileTypeDetector.detect(content)
                .filter(ALLOWED::contains)
                .orElseThrow(() -> new ApiException(CommonErrors.FILE_TYPE_NOT_ALLOWED));
        String sha256 = sha256(content);

        String targetVisibility = "PUBLIC".equals(visibility) ? "PUBLIC" : "PRIVATE";
        var existing = files.findFirstBySha256AndDeletedAtIsNull(sha256);
        if (existing.isPresent()) {
            SysFile same = existing.get();
            if (same.getVisibility().equals(targetVisibility)) {
                return new FileView(same.getId(), same.getOriginalName(), same.getMime(),
                        same.getSizeBytes(), same.getVisibility());
            }
            // 同一内容、不同可见性：复用已存储对象，仅新增记录
            SysFile copy = new SysFile(UUID.randomUUID(), same.getStorage(), same.getObjectKey(),
                    safeName(upload.getOriginalFilename()), same.getMime(), same.getSizeBytes(), sha256,
                    targetVisibility, ownerId, Instant.now());
            files.save(copy);
            return new FileView(copy.getId(), copy.getOriginalName(), copy.getMime(),
                    copy.getSizeBytes(), copy.getVisibility());
        }

        String objectKey = objectKeyFor(mime);
        try {
            storage.put(objectKey, content);
        } catch (IOException ex) {
            throw new ApiException(CommonErrors.SYS_DEPENDENCY_UNAVAILABLE);
        }

        SysFile file = new SysFile(UUID.randomUUID(), storage.name(), objectKey,
                safeName(upload.getOriginalFilename()), mime, content.length, sha256,
                targetVisibility, ownerId, Instant.now());
        files.save(file);
        return new FileView(file.getId(), file.getOriginalName(), file.getMime(),
                file.getSizeBytes(), file.getVisibility());
    }

    @Transactional(readOnly = true)
    public FileDownload load(UUID id, boolean publicOnly) {
        SysFile file = files.findByIdAndDeletedAtIsNull(id)
                .filter(f -> !publicOnly || f.isPublic())
                .orElseThrow(() -> new ApiException(CommonErrors.FILE_NOT_FOUND));
        byte[] content;
        try {
            content = storage.get(file.getObjectKey());
        } catch (IOException ex) {
            throw new ApiException(CommonErrors.FILE_NOT_FOUND);
        }
        return new FileDownload(file.getId(), file.getOriginalName(), file.getMime(), content);
    }

    private String objectKeyFor(String mime) {
        String ext = switch (mime) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "pdf";
        };
        LocalDate now = LocalDate.now();
        return "%d/%02d/%s.%s".formatted(now.getYear(), now.getMonthValue(), UUID.randomUUID(), ext);
    }

    private String safeName(String original) {
        if (original == null || original.isBlank()) {
            return "file";
        }
        String name = original.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1).replace("..", "_");
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}

