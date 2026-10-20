package xyz.byqy.vane.file.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class FileTypeDetectorTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Test
    void detectsPngByMagicBytes() {
        assertThat(FileTypeDetector.detect(PNG)).contains("image/png");
    }

    @Test
    void detectsPdf() {
        byte[] pdf = "%PDF-1.7 fake".getBytes();
        assertThat(FileTypeDetector.detect(pdf)).contains("application/pdf");
    }

    @Test
    void rejectsUnknownContent() {
        assertThat(FileTypeDetector.detect("hello world!".getBytes())).isEmpty();
    }

    @Test
    void classifiesImages() {
        assertThat(FileTypeDetector.isImage("image/webp")).isTrue();
        assertThat(FileTypeDetector.isImage("application/pdf")).isFalse();
    }
}
