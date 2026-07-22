package com.moneyflowbackend.emergencyfund.dto;

import com.moneyflowbackend.emergencyfund.model.EmergencyFundPlanStatus;
import lombok.Data;

@Data
public class EmergencyFundPlanStatusRequest {
    private EmergencyFundPlanStatus planStatus;
}
