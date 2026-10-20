package xyz.byqy.vane.api.file;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.audit.api.Audit;
import xyz.byqy.vane.file.application.FileAdminService;

/** 附件管理（ADM-09）。 */
@RestController
@RequestMapping("/api/v1/admin/files")
public class AdminFileController {

    private final FileAdminService files;

    public AdminFileController(FileAdminService files) {
        this.files = files;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_FILE.code)")
    public FileAdminService.PageResult<FileAdminService.FileAdminView> list(
            @RequestParam(name = "page[number]", defaultValue = "0") int pageNumber,
            @RequestParam(name = "page[size]", defaultValue = "20") int pageSize) {
        return files.page(pageNumber, pageSize);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).OPS_FILE_DELETE.code)")
    @Audit(module = "file", action = "delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        files.delete(id);
        return ResponseEntity.noContent().build();
    }
}
