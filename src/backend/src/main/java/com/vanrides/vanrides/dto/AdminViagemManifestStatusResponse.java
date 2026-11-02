package com.vanrides.vanrides.dto;

public class AdminViagemManifestStatusResponse {

    private final AdminViagemManifestStatus status;
    private final int mudancas;
    private final int adicionados;
    private final int removidos;
    private final String mensagem;

    public AdminViagemManifestStatusResponse(AdminViagemManifestStatus status,
                                             int mudancas,
                                             int adicionados,
                                             int removidos,
                                             String mensagem) {
        this.status = status == null ? AdminViagemManifestStatus.NOT_APPLICABLE : status;
        this.mudancas = Math.max(mudancas, 0);
        this.adicionados = Math.max(adicionados, 0);
        this.removidos = Math.max(removidos, 0);
        this.mensagem = mensagem;
    }

    public static AdminViagemManifestStatusResponse notApplicable() {
        return new AdminViagemManifestStatusResponse(
                AdminViagemManifestStatus.NOT_APPLICABLE,
                0,
                0,
                0,
                "Manifesto gerenciado fora de serviço recorrente."
        );
    }

    public static AdminViagemManifestStatusResponse upToDate(int paradas) {
        return new AdminViagemManifestStatusResponse(
                AdminViagemManifestStatus.UP_TO_DATE,
                0,
                0,
                0,
                paradas == 1 ? "Manifesto atualizado com 1 parada." : "Manifesto atualizado com " + paradas + " paradas."
        );
    }

    public static AdminViagemManifestStatusResponse pending(int adicionados, int removidos) {
        int mudancas = Math.max(adicionados, 0) + Math.max(removidos, 0);
        return new AdminViagemManifestStatusResponse(
                AdminViagemManifestStatus.PENDING,
                mudancas,
                adicionados,
                removidos,
                "Sincronize o manifesto antes da saída."
        );
    }

    public static AdminViagemManifestStatusResponse blocked(String mensagem) {
        return new AdminViagemManifestStatusResponse(
                AdminViagemManifestStatus.BLOCKED,
                0,
                0,
                0,
                mensagem == null || mensagem.isBlank()
                        ? "Revise o manifesto manualmente."
                        : mensagem
        );
    }

    public AdminViagemManifestStatus getStatus() {
        return status;
    }

    public int getMudancas() {
        return mudancas;
    }

    public int getAdicionados() {
        return adicionados;
    }

    public int getRemovidos() {
        return removidos;
    }

    public String getMensagem() {
        return mensagem;
    }
}
