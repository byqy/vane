package xyz.byqy.vane.iam.application;

import java.util.List;

/** 集合响应：items + page（03 §1），不使用外层信封。 */
public record PageResult<T>(List<T> items, PageMeta page) {

    public record PageMeta(int number, int size, long total) {
    }
}
