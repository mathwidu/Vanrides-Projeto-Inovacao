package com.vanrides.vanrides.model;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashSet;

import static org.junit.jupiter.api.Assertions.*;

class ServicoRecorrenteDomainModelTest {

    @Test
    void operaNaDataRespeitaDiasConfigurados() {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setDiasOperacaoFromSet(new LinkedHashSet<>(java.util.List.of(
                DayOfWeek.MONDAY,
                DayOfWeek.WEDNESDAY
        )));

        assertTrue(servico.operaNaData(LocalDate.of(2026, 5, 25))); // Monday
        assertFalse(servico.operaNaData(LocalDate.of(2026, 5, 26))); // Tuesday
    }

    @Test
    void defaultsRepresentamServicoRecorrenteCompativelComRotaBaseAtual() {
        ServicoRecorrente servico = new ServicoRecorrente();

        assertEquals(TipoOperacaoServico.IDA, servico.getTipoOperacao());
        assertEquals(PoliticaPresencaServico.INCLUIR_POR_PADRAO, servico.getPoliticaPresenca());
        assertTrue(servico.isAtivo());
        assertTrue(servico.isGeracaoAutomaticaAtiva());
    }

    @Test
    void servicoPassageiroValidaVigenciaOperacional() {
        ServicoPassageiro vinculo = new ServicoPassageiro();
        vinculo.setAtivo(true);
        vinculo.setDataInicio(LocalDate.of(2026, 5, 20));
        vinculo.setDataFim(LocalDate.of(2026, 5, 30));

        assertFalse(vinculo.ativoNaData(LocalDate.of(2026, 5, 19)));
        assertTrue(vinculo.ativoNaData(LocalDate.of(2026, 5, 20)));
        assertTrue(vinculo.ativoNaData(LocalDate.of(2026, 5, 30)));
        assertFalse(vinculo.ativoNaData(LocalDate.of(2026, 5, 31)));
    }

    @Test
    void presencaServicoDiferenciaConfirmacaoEAusencia() {
        PresencaServico presenca = new PresencaServico();

        presenca.setStatus(PresencaServicoStatus.CONFIRMED);
        assertTrue(presenca.isConfirmado());
        assertFalse(presenca.isAusente());

        presenca.setStatus(PresencaServicoStatus.ABSENT);
        assertFalse(presenca.isConfirmado());
        assertTrue(presenca.isAusente());
    }
}
