package com.moneyflowbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "Email/username khong duoc de trong")
    private String identifier;

    @NotBlank(message = "Mat khau khong duoc de trong")
    private String password;
}