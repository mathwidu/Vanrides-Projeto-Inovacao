package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViagemManifestSyncServiceTest {

    @Mock
    private ViagemRepository viagemRepository;
    @Mock
    private ViagemParadaRepository viagemParadaRepository;
    @Mock
    private ViagemEtaCalculator viagemEtaCalculator;
    @Mock
    private ServicoOperacionalStopsResolver servicoOperacionalStopsResolver;

    @Spy
    private ServicoTripPolicy servicoTripPolicy = new ServicoTripPolicy();
    @Spy
    private ViagemSnapshotApplier viagemSnapshotApplier = new ViagemSnapshotApplier();

    @InjectMocks
    private ViagemManifestSyncService syncService;

    @Test
    void shouldSyncPlannedServiceTripManifestWithCurrentOperationalPassengers() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(16L, company, motorista);
        servico.setHorarioPartida(LocalTime.of(7, 25));
        servico.setCapacidadePlanejada(3);
        Passageiro passageiroRemovido = passageiro(21L);
        Passageiro passageiroMantido = passageiro(22L);
        Passageiro passageiroNovo = passageiro(23L);
        ServicoPassageiro paradaMantidaNoServico = servicoPassageiro(servico, passageiroMantido, 1);
        ServicoPassageiro paradaNovaNoServico = servicoPassageiro(servico, passageiroNovo, 2);
        Viagem viagem = viagem(company, servico, motorista);
        ViagemParada paradaRemovida = viagemParada(51L, viagem, passageiroRemovido, 1);
        ViagemParada paradaMantida = viagemParada(52L, viagem, passageiroMantido, 2);

        when(viagemRepository.findByIdAndCompanyId(40L, 66L)).thenReturn(Optional.of(viagem));
        when(servicoOperacionalStopsResolver.select(66L, servico, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of(paradaMantidaNoServico, paradaNovaNoServico));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(40L))
                .thenReturn(List.of(paradaRemovida, paradaMantida));
        when(viagemRepository.save(viagem)).thenReturn(viagem);

        Viagem sincronizada = syncService.sincronizarManifesto(66L, 40L);

        assertThat(sincronizada).isSameAs(viagem);
        assertThat(viagem.getRotaVersao()).isEqualTo(4);
        assertThat(viagem.getHorarioPartida()).isEqualTo(LocalTime.of(7, 25));
        ArgumentCaptor<List<ViagemParada>> paradasCaptor = ArgumentCaptor.forClass(List.class);
        verify(viagemEtaCalculator).recalculate(eq(viagem), paradasCaptor.capture());
        List<ViagemParada> paradasSincronizadas = paradasCaptor.getValue();
        assertThat(paradasSincronizadas).extracting(parada -> parada.getPassageiro().getId())
                .containsExactly(22L, 23L);
        assertThat(paradasSincronizadas.get(0)).isSameAs(paradaMantida);
        assertThat(paradaMantida.getOrdem()).isEqualTo(1);
        assertThat(paradasSincronizadas.get(1).getViagem()).isSameAs(viagem);
        assertThat(paradasSincronizadas.get(1).getOrdem()).isEqualTo(2);
        verify(viagemParadaRepository).deleteAll(List.of(paradaRemovida));
        verify(viagemParadaRepository).saveAll(paradasSincronizadas);
        verify(viagemRepository).save(viagem);
    }

    @Test
    void shouldPreviewManifestSyncWithoutMutatingTrip() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(16L, company, motorista);
        Passageiro passageiroRemovido = passageiro(21L);
        Passageiro passageiroMantido = passageiro(22L);
        Passageiro passageiroNovo = passageiro(23L);
        ServicoPassageiro paradaMantidaNoServico = servicoPassageiro(servico, passageiroMantido, 1);
        ServicoPassageiro paradaNovaNoServico = servicoPassageiro(servico, passageiroNovo, 2);
        Viagem viagem = viagem(company, servico, motorista);
        ViagemParada paradaRemovida = viagemParada(51L, viagem, passageiroRemovido, 1);
        ViagemParada paradaMantida = viagemParada(52L, viagem, passageiroMantido, 2);

        when(viagemRepository.findByIdAndCompanyId(40L, 66L)).thenReturn(Optional.of(viagem));
        when(servicoOperacionalStopsResolver.select(66L, servico, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of(paradaMantidaNoServico, paradaNovaNoServico));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(40L))
                .thenReturn(List.of(paradaRemovida, paradaMantida));

        ViagemManifestSyncService.ManifestSyncPreview preview = syncService.previewSincronizacaoManifesto(66L, 40L);

        assertThat(preview.viagemId()).isEqualTo(40L);
        assertThat(preview.servicoId()).isEqualTo(16L);
        assertThat(preview.paradasAtuais()).isEqualTo(2);
        assertThat(preview.paradasAposSincronizacao()).isEqualTo(2);
        assertThat(preview.mudancas()).isEqualTo(2);
        assertThat(preview.semMudancas()).isFalse();
        assertThat(preview.passageirosAdicionados()).extracting(ViagemManifestSyncService.ManifestSyncPassenger::passageiroId)
                .containsExactly(23L);
        assertThat(preview.passageirosRemovidos()).extracting(ViagemManifestSyncService.ManifestSyncPassenger::passageiroId)
                .containsExactly(21L);
        assertThat(preview.passageirosMantidos()).extracting(ViagemManifestSyncService.ManifestSyncPassenger::passageiroId)
                .containsExactly(22L);
        verify(viagemParadaRepository, never()).deleteAll(any());
        verify(viagemParadaRepository, never()).saveAll(any());
        verify(viagemRepository, never()).save(any(Viagem.class));
    }

    @Test
    void shouldRejectManifestSyncWhenTripAlreadyStarted() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(16L, company, motorista);
        Viagem viagem = viagem(company, servico, motorista);
        viagem.setStatus(ViagemStatus.IN_PROGRESS);

        when(viagemRepository.findByIdAndCompanyId(40L, 66L)).thenReturn(Optional.of(viagem));

        Throwable thrown = catchThrowable(() -> syncService.sincronizarManifesto(66L, 40L));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("manifesto");
        verify(viagemParadaRepository, never()).findAllWithPassageiroByViagemIdOrderByOrdemAsc(40L);
        verify(viagemRepository, never()).save(viagem);
    }

    private Company company(Long id) {
        Company company = new Company("VanRides Default", "default");
        company.setId(id);
        return company;
    }

    private Motorista motorista(Long id) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome("Motorista " + id);
        return motorista;
    }

    private Passageiro passageiro(Long id) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro " + id);
        return passageiro;
    }

    private ServicoRecorrente servico(Long id, Company company, Motorista motorista) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company);
        servico.setMotoristaPadrao(motorista);
        servico.setNome("Serviço " + id);
        servico.setPoliticaPresenca(PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        servico.setAtivo(true);
        return servico;
    }

    private ServicoPassageiro servicoPassageiro(ServicoRecorrente servico, Passageiro passageiro, int ordem) {
        ServicoPassageiro servicoPassageiro = new ServicoPassageiro();
        servicoPassageiro.setServicoRecorrente(servico);
        servicoPassageiro.setCompany(servico.getCompany());
        servicoPassageiro.setPassageiro(passageiro);
        servicoPassageiro.setOrdemPadrao(ordem);
        servicoPassageiro.setAtivo(true);
        return servicoPassageiro;
    }

    private Viagem viagem(Company company, ServicoRecorrente servico, Motorista motorista) {
        Viagem viagem = new Viagem();
        viagem.setId(40L);
        viagem.setCompany(company);
        viagem.setServicoRecorrente(servico);
        viagem.setMotorista(motorista);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 19));
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem.setRotaVersao(3);
        return viagem;
    }

    private ViagemParada viagemParada(Long id, Viagem viagem, Passageiro passageiro, int ordem) {
        ViagemParada parada = new ViagemParada();
        parada.setId(id);
        parada.setViagem(viagem);
        parada.setPassageiro(passageiro);
        parada.setOrdem(ordem);
        return parada;
    }
}
