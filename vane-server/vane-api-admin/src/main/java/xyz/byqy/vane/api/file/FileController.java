package xyz.byqy.vane.api.file;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.common.audit.AuditContext;
import xyz.byqy.vane.file.application.FileDownload;
import xyz.byqy.vane.file.application.FileService;
import xyz.byqy.vane.file.application.FileTypeDetector;
import xyz.byqy.vane.file.application.FileView;
import xyz.byqy.vane.iam.api.security.AuthenticatedUser;

/** 附件上传与下载（ADM-09、03 §15）。 */
@RestController
public class FileController {

    private final FileService files;

    public FileController(FileService files) {
        this.files = files;
    }

    @PostMapping("/api/v1/files")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_FILE_UPLOAD.code)")
    public ResponseEntity<FileView> upload(@RequestParam("file") MultipartFile file,
                                           @RequestParam(name = "visibility", defaultValue = "PRIVATE") String visibility,
                                           @AuthenticationPrincipal AuthenticatedUser actor) {
        FileView created = files.upload(file, visibility, actor == null ? null : actor.id());
        return ResponseEntity.created(URI.create("/api/v1/files/" + created.id())).body(created);
    }

    @GetMapping("/api/v1/files/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_FILE_DOWNLOAD.code)")
    @Audit(module = "file", action = "download", targetType = "file", targetId = "#id")
    public ResponseEntity<byte[]> download(@PathVariable java.util.UUID id) {
        AuditContext.setAffectedCount(1);
        return response(files.load(id, false), true);
    }

    @GetMapping("/api/v1/public/files/{id}")
    public ResponseEntity<byte[]> publicDownload(@PathVariable java.util.UUID id) {
        FileDownload file = files.load(id, true);
        return response(file, !FileTypeDetector.isImage(file.mime()));
    }

    private ResponseEntity<byte[]> response(FileDownload file, boolean attachment) {
        String disposition = (attachment ? "attachment" : "inline") + "; filename*=UTF-8''"
                + URLEncoder.encode(file.filename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .body(file.content());
    }
}
