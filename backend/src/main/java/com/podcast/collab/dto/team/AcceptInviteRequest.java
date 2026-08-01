package com.podcast.collab.dto.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AcceptInviteRequest {

    @NotBlank(message = "Token is required")
    private String token;

    private String name;

    @Size(min = 10, message = "Password must be at least 10 characters")
    private String password;
}
