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
    private String source;
    private Boolean degraded;
    private String fallbackReason;
    private Integer scanLimit;

    public static <T> PageResult<T> of(List<T> items, String nextCursor, Boolean hasMore) {
        List<T> safeItems = items == null ? List.of() : items;
        return PageResult.<T>builder()
                .items(safeItems)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .total((long) safeItems.size())
                .build();
    }

    public static <T> PageResult<T> empty() {
        return PageResult.<T>builder().items(List.of()).nextCursor(null).hasMore(false).total(0L).build();
    }

    public PageResult<T> withMetadata(String source, boolean degraded, String fallbackReason, Integer scanLimit) {
        this.source = source;
        this.degraded = degraded;
        this.fallbackReason = fallbackReason;
        this.scanLimit = scanLimit;
        return this;
    }
}
