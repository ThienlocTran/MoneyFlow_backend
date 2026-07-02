package com.moneyflowbackend.wallet.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletResponse {
    private UUID id;
    private String name;
    private String type;
    private BigDecimal openingBalance;
    private LocalDate openingDate;
    private BigDecimal currentBalance;
    @JsonProperty("isDefault")
    private boolean isDefault;
    @JsonProperty("isActive")
    private boolean isActive;
    @JsonProperty("includeInTotal")
    private boolean includeInTotal;
    private Instant createdAt;
    private Instant updatedAt;
}
