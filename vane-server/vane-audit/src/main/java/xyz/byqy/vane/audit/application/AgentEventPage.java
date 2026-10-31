package xyz.byqy.vane.audit.application;

import java.util.List;

/**
 * 审计事件的一页（游标分页，docs/03 §9）。
 *
 * @param items 本页事件，按时间升序
 * @param page  分页元数据：游标与是否还有下一页
 */
public record AgentEventPage(List<AgentEvent> items, PageMeta page) {

    /**
     * @param size    每页条数
     * @param after   下一页游标；为 null 表示已到末尾
     * @param hasNext 是否还有下一页
     */
    public record PageMeta(int size, String after, boolean hasNext) {
    }
}