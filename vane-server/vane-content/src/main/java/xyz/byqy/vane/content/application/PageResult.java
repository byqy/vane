package xyz.byqy.vane.content.application;

import java.util.List;

public record PageResult<T>(List<T> items, PageMeta page) {

    public record PageMeta(int number, int size, long total) {
    }
}
