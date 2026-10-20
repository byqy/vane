package xyz.byqy.vane.content.application;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;
import xyz.byqy.vane.content.domain.CmsTag;
import xyz.byqy.vane.content.domain.CmsTagRepository;

/**
 * 文章标签（ADM-08）：标签展示名直接使用 code；删除为软删除，
 * 公开与后台查询都会过滤已删除标签，历史关联行保留。
 */
@Service
public class TagService {

    private final CmsTagRepository tags;
    private final JdbcTemplate jdbc;

    public TagService(CmsTagRepository tags, JdbcTemplate jdbc) {
        this.tags = tags;
        this.jdbc = jdbc;
    }

    @Transactional
    public TagView create(String code) {
        String normalized = code.strip();
        if (tags.existsByCodeAndDeletedAtIsNull(normalized)) {
            throw new ApiException(CommonErrors.CONTENT_TAG_CODE_TAKEN);
        }
        CmsTag tag = CmsTag.create(UUID.randomUUID(), normalized);
        tags.saveAndFlush(tag);
        return new TagView(tag.getId(), tag.getCode(), 0, tag.getCreatedAt());
    }

    @Transactional
    public void delete(UUID id) {
        CmsTag tag = tags.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ApiException(CommonErrors.CONTENT_TAG_NOT_FOUND));
        tag.softDelete();
        tags.save(tag);
    }

    /** 后台标签列表：带未删除文章使用计数。 */
    @Transactional(readOnly = true)
    public List<TagView> adminList() {
        return jdbc.query("""
                select t.id, t.code, t.created_at,
                       (select count(*) from cms_article_tag at
                          join cms_article a on a.id = at.article_id and a.deleted_at is null
                         where at.tag_id = t.id) as article_count
                  from cms_tag t
                 where t.deleted_at is null
                 order by t.code
                """, (rs, rowNum) -> new TagView(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getLong("article_count"), rs.getTimestamp("created_at") == null
                        ? null : rs.getTimestamp("created_at").toInstant()));
    }

    /** 门户标签列表：只统计已发布文章。 */
    @Transactional(readOnly = true)
    public List<PublicTagView> publicList() {
        return jdbc.query("""
                select t.id, t.code,
                       (select count(*) from cms_article_tag at
                          join cms_article a on a.id = at.article_id
                         where at.tag_id = t.id and a.status = 'PUBLISHED' and a.deleted_at is null) as article_count
                  from cms_tag t
                 where t.deleted_at is null
                 order by t.code
                """, (rs, rowNum) -> new PublicTagView(rs.getObject("id", UUID.class), rs.getString("code"),
                rs.getLong("article_count")));
    }
}
