package com.offerlab.community.common.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 游标分页响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private String nextCursor;
    private Boolean hasMore;
    private Long total;

    public static <T> PageResult<T> of(List<T> items, String nextCursor, Boolean hasMore) {
        return PageResult.<T>builder().items(items).nextCursor(nextCursor).hasMore(hasMore).build();
    }

    public static <T> PageResult<T> empty() {
        return PageResult.<T>builder().items(List.of()).nextCursor(null).hasMore(false).build();
    }
}
