package com.offerlab.community.search.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchStatusDTO {
    private String status;
    private boolean enabled;
    private boolean available;
    private String indexName;
    private boolean indexExists;
    private boolean indexReady;
    private Boolean publicSearchAvailable;
    private Boolean publicSearchDegraded;
    private String publicSearchSource;
    private Boolean dbFallbackAvailable;
    private String fallbackSource;
    private String fallbackMode;
    private Integer fallbackScanLimit;
    private Boolean fallbackSchemaReady;
    private String message;
    private String diagnosticMessage;
    private String action;
}
