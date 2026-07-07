package com.offerlab.community.interaction.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteFolderDTO {
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private Integer visibility;
    private Integer sortOrder;
    private Boolean defaultFolder;
    private Boolean privateFolder;
    private Long postCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
