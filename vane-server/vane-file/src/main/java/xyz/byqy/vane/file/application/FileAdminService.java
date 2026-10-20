package xyz.byqy.vane.file.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.file.domain.SysFile;
import xyz.byqy.vane.file.domain.SysFileRepository;

/** 附件管理（ADM-09）：列表与软删除。 */
@Service
public class FileAdminService {

    private final SysFileRepository files;

    public FileAdminService(SysFileRepository files) {
        this.files = files;
    }

    @Transactional(readOnly = true)
    public PageResult<FileAdminView> page(int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        Page<SysFile> page = files.findByDeletedAtIsNull(
                PageRequest.of(number, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PageResult<>(page.getContent().stream()
                .map(this::toView)
                .toList(),
                new PageResult.PageMeta(number, size, page.getTotalElements()));
    }

    @Transactional
    public void delete(UUID id) {
        SysFile file = files.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.FILE_NOT_FOUND));
        file.softDelete();
        files.save(file);
    }

    private FileAdminView toView(SysFile file) {
        return new FileAdminView(file.getId(), file.getOriginalName(), file.getMime(), file.getSizeBytes(),
                file.getVisibility(), file.getCreatedAt());
    }

    public record FileAdminView(UUID id, String name, String mime, long size, String visibility,
                                Instant createdAt) {
    }

    public record PageResult<T>(java.util.List<T> items, PageMeta page) {
        public record PageMeta(int number, int size, long total) {
        }
    }
}
