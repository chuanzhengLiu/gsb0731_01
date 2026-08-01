package com.podcast.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. A new user registering creates their own team and
 * becomes its ADMIN (README §3.1). Password policy per README §3.2:
 * min 10 chars, letters + digits + special characters.
 */
public record RegisterRequest(
        @Email @NotBlank String email,
        @NotBlank String name,
        @NotBlank
        @Size(min = 10, message = "密码至少10位")
        @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
            message = "密码必须包含字母、数字和特殊字符")
        String password,
        @NotBlank String teamName
) {}
