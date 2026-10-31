package xyz.byqy.vane.content.application;

import java.util.List;

public record PageResult<T>(List<T> items, PageMeta page) {

    /**
     * @param number  页码（页码分页时使用）
     * @param size    每页条数
     * @param total   总条数
     * @param after   下一页游标；为 null 表示已到末尾（docs/03 §9）
     * @param hasNext 是否还有下一页
     */
    public record PageMeta(int number, int size, long total, String after, boolean hasNext) {
    }
}
