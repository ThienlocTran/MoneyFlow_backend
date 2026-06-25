package com.moneyflowbackend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Username khong duoc de trong")
    @Size(min = 4, max = 40, message = "Username phai tu 4 den 40 ky tu")
    @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "Username chi gom chu, so, gach duoi va dau cham")
    private String username;

    @NotBlank(message = "Email khong duoc de trong")
    @Email(message = "Email khong dung dinh dang")
    private String email;

    @NotBlank(message = "Mat khau khong duoc de trong")
    @Size(min = 8, message = "Mat khau phai tu 8 ky tu tro len")
    private String password;

    @NotBlank(message = "Ho ten khong duoc de trong")
    @Size(max = 120, message = "Ho ten khong qua 120 ky tu")
    private String fullName;
}