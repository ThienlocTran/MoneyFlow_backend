package com.moneyflowbackend.category.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryKeywordResponse {
    private UUID id;
    private UUID categoryId;
    private String keyword;
    private Integer priority;
    private boolean isUserLearned;
}
