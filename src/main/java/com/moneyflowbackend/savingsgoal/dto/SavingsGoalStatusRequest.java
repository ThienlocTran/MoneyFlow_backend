package com.moneyflowbackend.savingsgoal.dto;

import com.moneyflowbackend.savingsgoal.model.SavingsGoalStatus;
import lombok.Data;

@Data
public class SavingsGoalStatusRequest {
    private SavingsGoalStatus status;
}
