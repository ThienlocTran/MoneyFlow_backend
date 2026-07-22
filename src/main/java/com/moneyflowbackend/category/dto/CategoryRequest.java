package com.moneyflowbackend.category.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.moneyflowbackend.common.model.SpendingScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.UUID;

@Data
public class CategoryRequest {
    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 120, message = "Tên danh mục không quá 120 ký tự")
    private String name;

    @NotNull(message = "Loại danh mục không được để trống")
    @JsonAlias("categoryType")
    private String type;

    private UUID jarId;
    private SpendingScope defaultSpendingScope;
    private String icon;
    private Boolean isActive;
    private Boolean isQuickAction;
    private Integer displayOrder;
}
