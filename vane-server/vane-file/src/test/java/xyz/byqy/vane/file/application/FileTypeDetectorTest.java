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
    void detectsMp4ByFtypBox() {
        byte[] mp4 = new byte[]{0x00, 0x00, 0x00, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        assertThat(FileTypeDetector.detect(mp4)).contains("video/mp4");
    }

    @Test
    void detectsWebmByEbmlMagic() {
        byte[] webm = new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x02, 0x03, 0x04,
                0x05, 0x06, 0x07, 0x08};
        assertThat(FileTypeDetector.detect(webm)).contains("video/webm");
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
