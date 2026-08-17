package com.offerlab.community.search.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public search availability contract. It intentionally excludes index,
 * fallback, schema, and diagnostic details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicSearchStatusDTO {
    private boolean available;
    private boolean degraded;
    private String message;
    private String action;
}
