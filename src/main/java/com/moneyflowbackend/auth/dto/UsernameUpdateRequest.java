package com.moneyflowbackend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UsernameUpdateRequest {
    @NotBlank(message = "Username khong duoc de trong")
    @Size(min = 4, max = 40, message = "Username phai tu 4 den 40 ky tu")
    @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Username chi gom chu, so, gach duoi va dau cham")
    private String username;
}
