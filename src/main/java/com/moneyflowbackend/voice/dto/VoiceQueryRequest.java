package com.moneyflowbackend.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceQueryRequest {
    @Size(max = 500)
    private String text;
    private String timezone;
    private OffsetDateTime now;
}
