package com.moneyflowbackend.wallet.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class WalletRequest {
    @NotBlank(message = "Tên ví không được để trống")
    @Size(max = 120, message = "Tên ví không quá 120 ký tự")
    private String name;

    @NotNull(message = "Loại ví không được để trống")
    @JsonAlias("type")
    private String walletType;

    private BigDecimal openingBalance;
    private LocalDate openingDate;
    private Boolean isDefault;
    private Boolean includeInTotal;
}
