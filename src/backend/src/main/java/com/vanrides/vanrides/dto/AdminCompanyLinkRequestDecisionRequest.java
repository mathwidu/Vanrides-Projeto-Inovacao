package com.vanrides.vanrides.dto;

import java.util.List;

public record AdminCompanyLinkRequestDecisionRequest(
        Long motoristaId,
        String reason,
        List<Long> servicoIds
) {
}
