package com.offerlab.community.infra.review;

import java.util.Objects;

public record ReviewQueueReopenRequestedEvent(ReviewQueueItemCommand command) {

    public ReviewQueueReopenRequestedEvent {
        Objects.requireNonNull(command, "command");
    }
}
