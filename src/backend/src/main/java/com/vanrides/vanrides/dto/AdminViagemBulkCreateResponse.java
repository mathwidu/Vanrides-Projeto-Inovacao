package com.vanrides.vanrides.dto;

import java.time.LocalDate;
import java.util.List;

public class AdminViagemBulkCreateResponse {

    private final LocalDate dataOperacao;
    private final int criadas;
    private final int existentes;
    private final int atualizadas;
    private final int bloqueadas;
    private final int ignoradas;
    private final List<AdminViagemSummaryResponse> viagens;
    private final List<AdminViagemBulkIgnoredResponse> servicosIgnorados;
    private final List<AdminViagemBulkIgnoredResponse> servicosBloqueados;

    public AdminViagemBulkCreateResponse(LocalDate dataOperacao,
                                         int criadas,
                                         int existentes,
                                         int ignoradas,
                                         List<AdminViagemSummaryResponse> viagens) {
        this(dataOperacao, criadas, existentes, ignoradas, viagens, List.of());
    }

    public AdminViagemBulkCreateResponse(LocalDate dataOperacao,
                                         int criadas,
                                         int existentes,
                                         int ignoradas,
                                         List<AdminViagemSummaryResponse> viagens,
                                         List<AdminViagemBulkIgnoredResponse> servicosIgnorados) {
        this(dataOperacao, criadas, existentes, 0, 0, ignoradas, viagens, servicosIgnorados, List.of());
    }

    public AdminViagemBulkCreateResponse(LocalDate dataOperacao,
                                         int criadas,
                                         int existentes,
                                         int atualizadas,
                                         int bloqueadas,
                                         int ignoradas,
                                         List<AdminViagemSummaryResponse> viagens,
                                         List<AdminViagemBulkIgnoredResponse> servicosIgnorados,
                                         List<AdminViagemBulkIgnoredResponse> servicosBloqueados) {
        this.dataOperacao = dataOperacao;
        this.criadas = criadas;
        this.existentes = existentes;
        this.atualizadas = atualizadas;
        this.bloqueadas = bloqueadas;
        this.ignoradas = ignoradas;
        this.viagens = viagens == null ? List.of() : viagens;
        this.servicosIgnorados = servicosIgnorados == null ? List.of() : servicosIgnorados;
        this.servicosBloqueados = servicosBloqueados == null ? List.of() : servicosBloqueados;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public int getCriadas() {
        return criadas;
    }

    public int getExistentes() {
        return existentes;
    }

    public int getAtualizadas() {
        return atualizadas;
    }

    public int getBloqueadas() {
        return bloqueadas;
    }

    public int getIgnoradas() {
        return ignoradas;
    }

    public List<AdminViagemSummaryResponse> getViagens() {
        return viagens;
    }

    public List<AdminViagemBulkIgnoredResponse> getServicosIgnorados() {
        return servicosIgnorados;
    }

    public List<AdminViagemBulkIgnoredResponse> getServicosBloqueados() {
        return servicosBloqueados;
    }
}
