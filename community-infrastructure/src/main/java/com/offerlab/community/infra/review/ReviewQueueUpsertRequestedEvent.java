package com.offerlab.community.infra.review;

import java.util.Objects;

public record ReviewQueueUpsertRequestedEvent(ReviewQueueItemCommand command) {

    public ReviewQueueUpsertRequestedEvent {
        Objects.requireNonNull(command, "command");
    }
}
