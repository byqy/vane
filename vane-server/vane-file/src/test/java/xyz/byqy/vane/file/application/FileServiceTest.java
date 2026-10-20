package xyz.byqy.vane.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.file.domain.SysFile;
import xyz.byqy.vane.file.domain.SysFileRepository;

class FileServiceTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    private final SysFileRepository files = mock(SysFileRepository.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final FileService service = new FileService(files, storage, 20);

    @Test
    void rejectsOversizedFile() {
        FileService tiny = new FileService(files, storage, 0);
        MockMultipartFile upload = new MockMultipartFile("file", "a.png", "image/png", PNG);

        assertThatThrownBy(() -> tiny.upload(upload, "PRIVATE", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.FILE_TOO_LARGE);
    }

    @Test
    void rejectsSpoofedContentType() {
        MockMultipartFile upload = new MockMultipartFile("file", "a.png", "image/png", "not an image".getBytes());

        assertThatThrownBy(() -> service.upload(upload, "PRIVATE", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).errorCode())
                .isEqualTo(CommonErrors.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void sameContentAndVisibilityReusesRecord() {
        SysFile existing = new SysFile(UUID.randomUUID(), "LOCAL", "2026/10/x.png", "a.png",
                "image/png", PNG.length, "sha", "PRIVATE", UUID.randomUUID(), Instant.now());
        when(files.findFirstBySha256AndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existing));

        FileView view = service.upload(new MockMultipartFile("file", "a.png", "image/png", PNG),
                "PRIVATE", UUID.randomUUID());

        assertThat(view.id()).isEqualTo(existing.getId());
    }

    @Test
    void sameContentDifferentVisibilityCreatesNewRecordReusingObject() {
        SysFile existing = new SysFile(UUID.randomUUID(), "LOCAL", "2026/10/x.png", "a.png",
                "image/png", PNG.length, "sha", "PRIVATE", UUID.randomUUID(), Instant.now());
        when(files.findFirstBySha256AndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existing));
        when(files.save(any(SysFile.class))).thenAnswer(inv -> inv.getArgument(0));

        FileView view = service.upload(new MockMultipartFile("file", "a.png", "image/png", PNG),
                "PUBLIC", UUID.randomUUID());

        assertThat(view.id()).isNotEqualTo(existing.getId());
        assertThat(view.visibility()).isEqualTo("PUBLIC");
        verify(files).save(any(SysFile.class));
    }
}
