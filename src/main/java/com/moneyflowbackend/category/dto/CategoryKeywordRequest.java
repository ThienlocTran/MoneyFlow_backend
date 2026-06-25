package com.moneyflowbackend.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CategoryKeywordRequest {
    @NotBlank(message = "Keyword is required")
    @Size(max = 120, message = "Keyword must be at most 120 characters")
    private String keyword;
    private Integer priority;
    private Boolean isUserLearned;
}
