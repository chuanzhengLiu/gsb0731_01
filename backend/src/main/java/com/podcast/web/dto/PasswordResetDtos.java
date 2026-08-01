package com.podcast.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordResetDtos {

    public record ForgotPasswordRequest(
            @Email @NotBlank String email
    ) {}

    /** New password must satisfy README §3.2 policy (same as registration). */
    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank
            @Size(min = 10, message = "密码至少10位")
            @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "密码必须包含字母、数字和特殊字符")
            String password
    ) {}
}
