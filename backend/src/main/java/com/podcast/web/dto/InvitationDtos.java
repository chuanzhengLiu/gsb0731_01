package com.podcast.web.dto;

import com.podcast.domain.Invitation;
import com.podcast.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class InvitationDtos {

    /** Admin invites a member by email with the role they will hold in the team. */
    public record CreateInvitationRequest(
            @Email @NotBlank String email,
            @NotNull Role roleInTeam
    ) {}

    /**
     * An invitee accepts the link, supplying their name + a password that
     * satisfies README §3.2 policy. This provisions their account.
     */
    public record AcceptInvitationRequest(
            @NotBlank String token,
            @NotBlank String name,
            @NotBlank
            @Size(min = 10, message = "密码至少10位")
            @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "密码必须包含字母、数字和特殊字符")
            String password
    ) {}

    public record InvitationResponse(
            Long id,
            Long teamId,
            String email,
            String roleInTeam,
            boolean accepted,
            boolean revoked,
            boolean expired,
            String expiresAt,
            String createdAt
    ) {
        public static InvitationResponse from(Invitation inv, boolean expired) {
            return new InvitationResponse(
                    inv.getId(), inv.getTeamId(), inv.getEmail(), inv.getRoleInTeam().name(),
                    inv.isAccepted(), inv.isRevoked(), expired,
                    inv.getExpiresAt().toString(), inv.getCreatedAt().toString());
        }
    }
}
