package xyz.byqy.vane.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * 文案资源门禁：
 * 1. 20 种语言的 key 集合必须与中文基准完全一致；
 * 2. 基础文件 messages.properties 是中文回退，必须与 messages_zh_CN.properties 逐条相同；
 * 3. 不允许空文案。
 */
class MessagesResourceTest {

    private static final String ZH_CN = "messages_zh_CN.properties";

    @Test
    void allLocaleBundlesShareSameKeysAndNoEmptyValues() throws IOException {
        Map<String, Path> bundles = bundles();
        // 1 个中文基础回退 + 20 个语言包
        assertThat(bundles).hasSize(21);
        assertThat(bundles).containsKey("messages.properties");
        assertThat(bundles).containsKey(ZH_CN);
        assertThat(bundles.keySet().stream().filter(name -> !name.equals("messages.properties")).count())
                .as("语言包数量")
                .isEqualTo(20);

        Set<String> expectedKeys = keys(bundles.get(ZH_CN));
        assertThat(expectedKeys).isNotEmpty();

        for (Map.Entry<String, Path> entry : bundles.entrySet()) {
            Properties properties = load(entry.getValue());
            assertThat(properties.stringPropertyNames())
                    .as("key 集合：%s", entry.getKey())
                    .isEqualTo(expectedKeys);
            for (String key : expectedKeys) {
                assertThat(properties.getProperty(key))
                        .as("空文案：%s / %s", entry.getKey(), key)
                        .isNotBlank();
            }
        }
    }

    @Test
    void baseBundleIsChineseFallback() throws IOException {
        Map<String, Path> bundles = bundles();
        Properties base = load(bundles.get("messages.properties"));
        Properties zhCn = load(bundles.get(ZH_CN));

        for (String key : zhCn.stringPropertyNames()) {
            assertThat(base.getProperty(key))
                    .as("基础文件应是中文回退，键 %s 与 zh_CN 不一致", key)
                    .isEqualTo(zhCn.getProperty(key));
        }
    }

    private static Map<String, Path> bundles() throws IOException {
        var url = MessagesResourceTest.class.getClassLoader().getResource("i18n");
        assertThat(url).isNotNull();
        Path dir;
        try {
            dir = Path.of(url.toURI());
        } catch (java.net.URISyntaxException ex) {
            throw new IOException("无法定位 i18n 资源目录", ex);
        }
        Map<String, Path> result = new LinkedHashMap<>();
        try (var stream = Files.list(dir)) {
            stream.filter(path -> path.getFileName().toString().matches("messages.*\\.properties"))
                    .sorted()
                    .forEach(path -> result.put(path.getFileName().toString(), path));
        }
        return result;
    }

    private static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path);
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Set<String> keys(Path path) throws IOException {
        return new TreeSet<>(load(path).stringPropertyNames());
    }

    @SuppressWarnings("unused")
    private static List<String> reservedForFutureAssertions() {
        return List.of();
    }
}


