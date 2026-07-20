package com.moneyflowbackend.obligation.dto;

import com.moneyflowbackend.transaction.dto.TransactionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmOccurrenceResponse {
    private ObligationOccurrenceResponse occurrence;
    private TransactionResponse transaction;
}
