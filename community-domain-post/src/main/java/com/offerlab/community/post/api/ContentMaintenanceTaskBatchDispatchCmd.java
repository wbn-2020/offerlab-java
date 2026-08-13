package com.offerlab.community.post.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Internal Post-domain command used by Analytics after it has revalidated a
 * channel-health candidate. It deliberately contains only the source
 * revision key and task execution metadata.
 */
@Data
public class ContentMaintenanceTaskBatchDispatchCmd {
    @NotNull
    @Positive
    private Long batchId;

    @NotNull
    private Integer domain;

    @NotNull
    @Positive
    private Long sourcePostId;

    @NotNull
    @Positive
    private Long sourceRefId;

    @NotNull
    @Positive
    private Long assigneeUid;

    @NotBlank
    @Size(max = 160)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String detail;

    @NotBlank
    @Size(max = 16)
    private String priority;

    private LocalDateTime dueAt;
}
