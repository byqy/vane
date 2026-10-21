package xyz.byqy.vane.system.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.system.domain.SysMessageTemplate;
import xyz.byqy.vane.system.domain.SysMessageTemplateRepository;

/**
 * 消息模板（ADM-11）：正文存库、按语言回退、{变量} 替换。
 * 发送由 {@link MailSendService} 负责（ADR-024），本类只做查询、编辑与渲染。
 */
@Service
public class MessageTemplateService {

    private static final Pattern VARIABLE = Pattern.compile("\\{(\\w+)}");
    private static final String DEFAULT_LOCALE = "zh-CN";

    private final SysMessageTemplateRepository templates;
    private final JdbcTemplate jdbc;
    public MessageTemplateService(SysMessageTemplateRepository templates, JdbcTemplate jdbc) {
        this.templates = templates;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list(String channel) {
        List<SysMessageTemplate> all = channel == null || channel.isBlank()
                ? templates.findByDeletedAtIsNullOrderByCodeAsc()
                : templates.findByChannelAndDeletedAtIsNullOrderByCodeAsc(channel);
        return all.stream().map(this::toView).toList();
    }

    @Transactional
    public TemplateView updateTranslations(UUID id, List<Translation> translations) {
        SysMessageTemplate template = templates.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.MAIL_TEMPLATE_NOT_FOUND));
        if (translations == null || translations.isEmpty()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        jdbc.update("delete from sys_message_template_i18n where template_id = ?", id);
        for (Translation translation : translations) {
            if (translation.locale() == null || translation.body() == null) {
                throw new ApiException(CommonErrors.VALIDATION_FAILED);
            }
            jdbc.update("""
                    insert into sys_message_template_i18n (template_id, locale, subject, body, updated_at)
                    values (?, ?, ?, ?, now())
                    """, id, translation.locale(), translation.subject(), translation.body());
        }
        return toView(template);
    }

    /** 渲染模板：请求语言 → zh-CN → 任意语言。 */
    @Transactional(readOnly = true)
    public Rendered render(String code, String channel, String locale, Map<String, String> variables) {
        SysMessageTemplate template = templates.findByCodeAndChannelAndDeletedAtIsNull(code, channel)
                .orElseThrow(() -> new ApiException(CommonErrors.MAIL_TEMPLATE_NOT_FOUND));
        if (!"ACTIVE".equals(template.getStatus())) {
            throw new ApiException(CommonErrors.MAIL_TEMPLATE_NOT_FOUND);
        }
        List<Translation> translations = translations(template.getId());
        Translation chosen = pick(translations, locale)
                .orElseThrow(() -> new ApiException(CommonErrors.MAIL_TEMPLATE_NOT_FOUND));
        return new Rendered(template.getCode(), template.getChannel(), chosen.locale(),
                fill(chosen.subject(), variables), fill(chosen.body(), variables));
    }

    private Optional<Translation> pick(List<Translation> translations, String locale) {
        if (locale != null) {
            Optional<Translation> exact = translations.stream()
                    .filter(t -> t.locale().equalsIgnoreCase(locale))
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }
        }
        return translations.stream()
                .filter(t -> t.locale().equals(DEFAULT_LOCALE))
                .findFirst()
                .or(() -> translations.stream().findFirst());
    }

    private String fill(String template, Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        Matcher matcher = VARIABLE.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = variables == null ? null : variables.get(key);
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "{" + key + "}" : value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private TemplateView toView(SysMessageTemplate template) {
        return new TemplateView(template.getId(), template.getCode(), template.getChannel(), template.getStatus(),
                template.getRemark(), translations(template.getId()));
    }

    private List<Translation> translations(UUID templateId) {
        return jdbc.query("""
                select locale, subject, body from sys_message_template_i18n
                where template_id = ? order by locale
                """, (rs, rowNum) -> new Translation(rs.getString("locale"), rs.getString("subject"),
                rs.getString("body")), templateId);
    }

    public record Translation(String locale, String subject, String body) {
    }

    public record TemplateView(UUID id, String code, String channel, String status, String remark,
                               List<Translation> translations) {
    }

    public record Rendered(String code, String channel, String locale, String subject, String body) {
    }
}
