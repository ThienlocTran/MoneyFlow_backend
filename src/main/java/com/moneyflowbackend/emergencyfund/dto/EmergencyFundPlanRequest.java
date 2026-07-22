package com.moneyflowbackend.emergencyfund.dto;

import com.moneyflowbackend.emergencyfund.model.EmergencyFundBasisMode;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EmergencyFundPlanRequest {
    private Integer targetMonths;
    private EmergencyFundBasisMode basisMode;
    private BigDecimal manualMonthlyExpense;
}
