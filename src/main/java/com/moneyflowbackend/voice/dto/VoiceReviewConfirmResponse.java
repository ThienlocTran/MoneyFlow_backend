package com.moneyflowbackend.voice.dto;

import com.moneyflowbackend.transaction.dto.TransactionResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceReviewConfirmResponse {
    private UUID transactionId;
    private UUID voiceRecordId;
    private String status;
    private String audioStatus;
    private TransactionResponse transaction;
}
