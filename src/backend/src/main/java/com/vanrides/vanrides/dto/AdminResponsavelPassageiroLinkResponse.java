package com.vanrides.vanrides.dto;

public class AdminResponsavelPassageiroLinkResponse {

    private final AdminGuardianSummaryResponse responsavel;
    private final AdminPassageiroLinkedSummaryResponse passageiro;

    public AdminResponsavelPassageiroLinkResponse(AdminGuardianSummaryResponse responsavel,
                                                  AdminPassageiroLinkedSummaryResponse passageiro) {
        this.responsavel = responsavel;
        this.passageiro = passageiro;
    }

    public AdminGuardianSummaryResponse getResponsavel() {
        return responsavel;
    }

    public AdminPassageiroLinkedSummaryResponse getPassageiro() {
        return passageiro;
    }
}
