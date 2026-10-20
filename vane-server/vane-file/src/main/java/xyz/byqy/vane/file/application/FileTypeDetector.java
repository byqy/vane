package xyz.byqy.vane.file.application;

import java.util.Optional;

/** 按魔数识别真实类型，不信任客户端声明的 Content-Type。 */
public final class FileTypeDetector {

    private FileTypeDetector() {
    }

    public static Optional<String> detect(byte[] content) {
        if (content == null || content.length < 12) {
            return Optional.empty();
        }
        if (startsWith(content, new int[]{0x89, 0x50, 0x4E, 0x47})) {
            return Optional.of("image/png");
        }
        if (startsWith(content, new int[]{0xFF, 0xD8, 0xFF})) {
            return Optional.of("image/jpeg");
        }
        if (startsWith(content, new int[]{0x47, 0x49, 0x46, 0x38})) {
            return Optional.of("image/gif");
        }
        if (startsWith(content, new int[]{0x52, 0x49, 0x46, 0x46}) && matches(content, 8, "WEBP")) {
            return Optional.of("image/webp");
        }
        if (matches(content, 0, "%PDF")) {
            return Optional.of("application/pdf");
        }
        return Optional.empty();
    }

    public static boolean isImage(String mime) {
        return mime != null && mime.startsWith("image/");
    }

    private static boolean startsWith(byte[] content, int[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if ((content[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matches(byte[] content, int offset, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            if (offset + i >= content.length || (content[offset + i] & 0xFF) != ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
