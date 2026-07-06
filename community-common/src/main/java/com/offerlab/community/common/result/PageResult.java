package com.offerlab.community.common.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private Map<String, Object> diagnostics;

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

    public PageResult<T> withDiagnostic(String key, Object value) {
        if (key == null || key.isBlank()) {
            return this;
        }
        if (this.diagnostics == null) {
            this.diagnostics = new LinkedHashMap<>();
        }
        this.diagnostics.put(key, value);
        return this;
    }

    public PageResult<T> withDiagnostics(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return this;
        }
        if (this.diagnostics == null) {
            this.diagnostics = new LinkedHashMap<>();
        }
        this.diagnostics.putAll(values);
        return this;
    }

    public PageResult<T> publicView() {
        return PageResult.<T>builder()
                .items(items == null ? List.of() : items)
                .nextCursor(nextCursor)
                .hasMore(Boolean.TRUE.equals(hasMore))
                .total(total == null ? 0L : total)
                .build();
    }
}
