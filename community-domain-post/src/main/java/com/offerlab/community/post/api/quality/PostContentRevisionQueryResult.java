package com.offerlab.community.post.api.quality;

import java.util.List;
import java.util.Objects;

public record PostContentRevisionQueryResult(
        List<PostContentRevisionSnapshot> items,
        boolean available
) {
    public PostContentRevisionQueryResult {
        items = items == null ? List.of() : items.stream()
                .filter(Objects::nonNull)
                .toList();
    }

    public static PostContentRevisionQueryResult available(List<PostContentRevisionSnapshot> items) {
        return new PostContentRevisionQueryResult(items, true);
    }

    public static PostContentRevisionQueryResult unavailable(List<PostContentRevisionSnapshot> items) {
        return new PostContentRevisionQueryResult(items, false);
    }
}
