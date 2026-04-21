package com.banking_software.BankingSoftware.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CreateUserRequest {
    @NotBlank
    private String firstName;
    private String lastName;

    @NotBlank @Email
    private String email;

    @NotBlank @Pattern(regexp = "\\d{10}", message = "phone must be 10 digits")
    private String phone;

    @Pattern(regexp = "[A-Z]{5}\\d{4}[A-Z]", message = "invalid PAN")
    private String pan;
}
