package xyz.byqy.vane.content.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsNotice;
import xyz.byqy.vane.content.domain.CmsNoticeRepository;

/**
 * 公告服务（ADM-08、D-09）：主体走 JPA，多语言正文走 cms_notice_i18n；
 * 门户按 Accept-Language 取译文，缺失回退 zh-CN，再回退任意语言。
 */
@Service
public class NoticeService {

    private final CmsNoticeRepository notices;
    private final JdbcTemplate jdbc;

    public NoticeService(CmsNoticeRepository notices, JdbcTemplate jdbc) {
        this.notices = notices;
        this.jdbc = jdbc;
    }

    @Transactional
    public NoticeView create(String level, boolean pinned, Instant publishAt, Instant expireAt,
                             List<NoticeTranslation> translations, UUID authorId) {
        requireTranslations(translations);
        CmsNotice notice = CmsNotice.create(UUID.randomUUID(), level, pinned, publishAt, expireAt, authorId);
        // 必须先 flush：多语言正文用 JdbcTemplate 写入，依赖主表行已存在
        notices.saveAndFlush(notice);
        replaceTranslations(notice.getId(), translations);
        return view(notice, translations);
    }

    @Transactional
    public NoticeView update(UUID id, String level, boolean pinned, Instant publishAt, Instant expireAt,
                             List<NoticeTranslation> translations) {
        CmsNotice notice = require(id);
        requireTranslations(translations);
        notice.update(level, pinned, publishAt, expireAt);
        notices.save(notice);
        replaceTranslations(id, translations);
        return view(notice, translations);
    }

    @Transactional
    public NoticeView publish(UUID id) {
        CmsNotice notice = require(id);
        if (notice.getPublishAt() == null) {
            notice.publishImmediately();
        } else {
            notice.publish(Instant.now());
        }
        notices.save(notice);
        return view(notice, translations(id));
    }

    @Transactional
    public void delete(UUID id) {
        CmsNotice notice = require(id);
        notice.softDelete();
        notices.save(notice);
    }

    @Transactional(readOnly = true)
    public PageResult<NoticeView> adminPage(int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        Page<CmsNotice> page = notices.findByDeletedAtIsNull(PageRequest.of(number, size,
                Sort.by(Sort.Direction.DESC, "pinned").and(Sort.by(Sort.Direction.DESC, "createdAt"))));
        List<NoticeView> items = page.getContent().stream()
                .map(n -> view(n, translations(n.getId())))
                .toList();
        return new PageResult<>(items, new PageResult.PageMeta(number, size, page.getTotalElements(), null, false));
    }

    @Transactional(readOnly = true)
    public PageResult<PublicNoticeView> publicPage(String locale, int pageNumber, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), 100);
        int number = Math.max(pageNumber, 0);
        String sql = """
                select n.id, n.level, n.pinned, n.publish_at,
                       coalesce(ti.title, tz.title, tf.title) as title,
                       coalesce(ti.body, tz.body, tf.body) as body
                from cms_notice n
                left join cms_notice_i18n ti on ti.notice_id = n.id and ti.locale = ?
                left join cms_notice_i18n tz on tz.notice_id = n.id and tz.locale = 'zh-CN'
                left join lateral (
                    select title, body from cms_notice_i18n where notice_id = n.id order by locale limit 1
                ) tf on true
                where n.status = 'PUBLISHED' and n.deleted_at is null
                  and (n.publish_at is null or n.publish_at <= now())
                  and (n.expire_at is null or n.expire_at > now())
                order by n.pinned desc, n.publish_at desc, n.created_at desc
                limit ? offset ?
                """;
        List<PublicNoticeView> items = jdbc.query(sql, (rs, rowNum) -> new PublicNoticeView(
                rs.getObject("id", UUID.class), rs.getString("level"), rs.getBoolean("pinned"),
                rs.getString("title"), rs.getString("body"), rs.getTimestamp("publish_at") == null
                        ? null : rs.getTimestamp("publish_at").toInstant()), locale, size, number * size);

        Long total = jdbc.queryForObject("""
                select count(*) from cms_notice n
                where n.status = 'PUBLISHED' and n.deleted_at is null
                  and (n.publish_at is null or n.publish_at <= now())
                  and (n.expire_at is null or n.expire_at > now())
                """, Long.class);
        return new PageResult<>(items, new PageResult.PageMeta(number, size, total == null ? 0 : total, null, false));
    }

    private NoticeView view(CmsNotice notice, List<NoticeTranslation> translations) {
        return new NoticeView(notice.getId(), notice.getLevel(), notice.getStatus(), notice.isPinned(),
                notice.getPublishAt(), notice.getExpireAt(), translations);
    }

    private List<NoticeTranslation> translations(UUID noticeId) {
        return jdbc.query("select locale, title, body from cms_notice_i18n where notice_id = ? order by locale",
                (rs, rowNum) -> new NoticeTranslation(rs.getString("locale"), rs.getString("title"),
                        rs.getString("body")), noticeId);
    }

    private void replaceTranslations(UUID noticeId, List<NoticeTranslation> translations) {
        jdbc.update("delete from cms_notice_i18n where notice_id = ?", noticeId);
        for (NoticeTranslation translation : translations) {
            jdbc.update("insert into cms_notice_i18n (notice_id, locale, title, body) values (?, ?, ?, ?)",
                    noticeId, translation.locale(), translation.title(), translation.body());
        }
    }

    private void requireTranslations(List<NoticeTranslation> translations) {
        if (translations == null || translations.isEmpty()) {
            throw new ApiException(CommonErrors.VALIDATION_FAILED);
        }
        for (NoticeTranslation translation : translations) {
            if (translation.locale() == null || translation.locale().isBlank()
                    || translation.title() == null || translation.title().isBlank()) {
                throw new ApiException(CommonErrors.VALIDATION_FAILED);
            }
        }
    }

    private CmsNotice require(UUID id) {
        return notices.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_NOT_FOUND));
    }
}


