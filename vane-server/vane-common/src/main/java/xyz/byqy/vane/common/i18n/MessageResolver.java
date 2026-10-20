package xyz.byqy.vane.common.i18n;

import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** 统一的消息解析：找不到译文时回退为 key，避免异常中断响应。 */
@Component
public class MessageResolver {

    private static final String BASE_LOCALE = "zh-CN";

    private final MessageSource messageSource;
    private final Set<String> supportedLocales;

    public MessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
        this.supportedLocales = scanSupportedLocales();
    }

    public String resolve(String key, Object... args) {
        return resolve(key, LocaleContextHolder.getLocale(), args);
    }

    public String resolve(String key, Locale locale, Object... args) {
        try {
            return messageSource.getMessage(key, args, locale);
        } catch (NoSuchMessageException ex) {
            return key;
        }
    }

    public Locale currentLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        return locale == null ? Locale.SIMPLIFIED_CHINESE : locale;
    }

    /** 支持的语言标签（由 i18n 资源文件推导，含中文基础包）。 */
    public Set<String> supportedLocales() {
        return supportedLocales;
    }

    public boolean isSupportedLocale(String tag) {
        if (tag == null) {
            return false;
        }
        String normalized = tag.strip().replace('_', '-');
        return supportedLocales.stream().anyMatch(item -> item.equalsIgnoreCase(normalized));
    }

    /** 统一为资源文件里的写法（如 zh_cn / ZH-CN → zh-CN），未知标签原样返回。 */
    public String normalizeLocale(String tag) {
        String normalized = tag == null ? null : tag.strip().replace('_', '-');
        if (normalized == null) {
            return null;
        }
        return supportedLocales.stream()
                .filter(item -> item.equalsIgnoreCase(normalized))
                .findFirst()
                .orElse(normalized);
    }

    private static Set<String> scanSupportedLocales() {
        Set<String> result = new TreeSet<>();
        result.add(BASE_LOCALE);
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:i18n/messages_*.properties");
            for (Resource resource : resources) {
                String name = resource.getFilename();
                if (name == null) {
                    continue;
                }
                String localePart = name.substring("messages_".length(), name.length() - ".properties".length());
                result.add(localePart.replace('_', '-'));
            }
        } catch (IOException ex) {
            // 资源扫描失败时只保留基础语言，不影响主流程
        }
        return Collections.unmodifiableSet(result);
    }
}

