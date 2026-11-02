package com.vanrides.vanrides.dto;

public class AdminPassageiroMotoristaLinkResponse {

    private final Long passageiroId;
    private final String passageiroNome;
    private final boolean menorDeIdade;
    private final AdminMotoristaSummaryResponse motorista;

    public AdminPassageiroMotoristaLinkResponse(Long passageiroId,
                                                String passageiroNome,
                                                boolean menorDeIdade,
                                                AdminMotoristaSummaryResponse motorista) {
        this.passageiroId = passageiroId;
        this.passageiroNome = passageiroNome;
        this.menorDeIdade = menorDeIdade;
        this.motorista = motorista;
    }

    public Long getPassageiroId() {
        return passageiroId;
    }

    public String getPassageiroNome() {
        return passageiroNome;
    }

    public boolean isMenorDeIdade() {
        return menorDeIdade;
    }

    public AdminMotoristaSummaryResponse getMotorista() {
        return motorista;
    }
}
