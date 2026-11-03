package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record AdminDriverInviteCreateRequest(
        OffsetDateTime expiresAt
) {
}
