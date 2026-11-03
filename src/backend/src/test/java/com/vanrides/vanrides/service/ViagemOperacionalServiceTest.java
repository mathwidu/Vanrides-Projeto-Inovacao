package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.PresencaRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViagemOperacionalServiceTest {

    @Mock
    private ViagemRepository viagemRepository;
    @Mock
    private ViagemParadaRepository viagemParadaRepository;
    @Mock
    private PresencaRepository presencaRepository;
    @Mock
    private PresencaServicoRepository presencaServicoRepository;

    @Mock
    private ViagemEtaCalculator viagemEtaCalculator;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ViagemProvisioningService viagemProvisioningService;

    @Mock
    private ViagemRotaGeograficaService viagemRotaGeograficaService;

    @Mock
    private ViagemRealtimePublisherService viagemRealtimePublisherService;

    @Mock
    private ViagemTrackingStateService viagemTrackingStateService;

    @InjectMocks
    private ViagemOperacionalService viagemOperacionalService;

    @Test
    void shouldReadDriverTripFromAutomaticProvisioning() {
        Viagem viagem = viagem(30L, 66L, 7L);
        ViagemParada parada = parada(300L, viagem, 1, passageiro(21L), ViagemParadaStatus.PENDING);

        when(viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(66L, 7L, LocalDate.of(2026, 3, 12)))
                .thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(parada));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(List.of(21L), 66L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(viagem, List.of(parada), null))
                .thenReturn(null);

        var response = viagemOperacionalService.buscarViagemDoMotorista(7L, LocalDate.of(2026, 3, 12), 66L);

        assertThat(response).isPresent();
        assertThat(response.get().getProximaParada().getPassageiro().getId()).isEqualTo(21L);
    }

    @Test
    void shouldReadDriverDayAgendaFromAutomaticProvisioning() {
        Viagem manha = viagem(30L, 66L, 7L);
        Viagem tarde = viagem(31L, 66L, 7L);
        ViagemParada paradaManha = parada(300L, manha, 1, passageiro(21L), ViagemParadaStatus.PENDING);
        ViagemParada paradaTarde = parada(301L, tarde, 1, passageiro(22L), ViagemParadaStatus.PENDING);

        when(viagemProvisioningService.buscarOuCriarViagensAutomaticasDoMotorista(66L, 7L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of(manha, tarde));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(paradaManha));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(31L)).thenReturn(List.of(paradaTarde));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(any(), eq(66L), eq(LocalDate.of(2026, 3, 12))))
                .thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(any(Viagem.class), any(), isNull()))
                .thenReturn(null);

        var response = viagemOperacionalService.buscarAgendaDoMotorista(7L, LocalDate.of(2026, 3, 12), 66L);

        assertThat(response).extracting("id").containsExactly(30L, 31L);
        assertThat(response).extracting("proximaParada.passageiro.id").containsExactly(21L, 22L);
    }

    @Test
    void shouldReadPassengerTripFromAutomaticProvisioning() {
        Viagem viagem = viagem(30L, 66L, 7L);
        ViagemParada parada = parada(300L, viagem, 1, passageiro(21L), ViagemParadaStatus.PENDING);

        when(viagemProvisioningService.buscarOuCriarViagemAutomaticaDoPassageiro(66L, 21L, LocalDate.of(2026, 3, 12)))
                .thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(parada));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(List.of(21L), 66L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(viagem, List.of(parada), 21L))
                .thenReturn(null);

        var response = viagemOperacionalService.buscarViagemAtualDoPassageiro(21L, LocalDate.of(2026, 3, 12), 66L);

        assertThat(response).isPresent();
        assertThat(response.get().getParadaDoPassageiro().getPassageiro().getId()).isEqualTo(21L);
    }

    @Test
    void shouldReadPassengerDayAgendaFromAutomaticProvisioning() {
        Viagem ida = viagem(30L, 66L, 7L);
        Viagem volta = viagem(31L, 66L, 8L);
        ViagemParada paradaIda = parada(300L, ida, 1, passageiro(21L), ViagemParadaStatus.PENDING);
        ViagemParada paradaVolta = parada(301L, volta, 1, passageiro(21L), ViagemParadaStatus.PENDING);

        when(viagemProvisioningService.buscarOuCriarViagensAutomaticasDoPassageiro(66L, 21L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of(ida, volta));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(paradaIda));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(31L)).thenReturn(List.of(paradaVolta));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(any(), eq(66L), eq(LocalDate.of(2026, 3, 12))))
                .thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(any(Viagem.class), any(), eq(21L)))
                .thenReturn(null);

        var response = viagemOperacionalService.buscarAgendaDoPassageiro(21L, LocalDate.of(2026, 3, 12), 66L);

        assertThat(response).extracting("id").containsExactly(30L, 31L);
        assertThat(response).extracting("paradaDoPassageiro.passageiro.id").containsExactly(21L, 21L);
    }

    @Test
    void shouldExposeRouteAndServiceNamesSeparatelyForDriverTrip() {
        Viagem viagem = viagem(30L, 66L, 7L);
        ServicoRecorrente servico = servico(15L, viagem.getCompany(), "Linha Manha");
        viagem.setServicoRecorrente(servico);
        viagem.setServicoNomeSnapshot("Linha Manha Snapshot");
        ViagemParada parada = parada(300L, viagem, 1, passageiro(21L), ViagemParadaStatus.PENDING);

        when(viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(66L, 7L, LocalDate.of(2026, 3, 12)))
                .thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(parada));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L), 66L, 15L, LocalDate.of(2026, 3, 12)
        )).thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(viagem, List.of(parada), null))
                .thenReturn(null);

        var response = viagemOperacionalService.buscarViagemDoMotorista(7L, LocalDate.of(2026, 3, 12), 66L);

        assertThat(response).isPresent();
        assertThat(response.get().getRotaNome()).isEqualTo("Rota Escolar");
        assertThat(response.get().getServicoNome()).isEqualTo("Linha Manha Snapshot");
    }

    @Test
    void shouldExposeRouteAndServiceNamesSeparatelyForPassengerTrip() {
        Viagem viagem = viagem(30L, 66L, 7L);
        ServicoRecorrente servico = servico(15L, viagem.getCompany(), "Linha Manha");
        viagem.setServicoRecorrente(servico);
        viagem.setServicoNomeSnapshot("Linha Manha Snapshot");
        ViagemParada parada = parada(300L, viagem, 1, passageiro(21L), ViagemParadaStatus.PENDING);

        when(viagemProvisioningService.buscarOuCriarViagemAutomaticaDoPassageiro(66L, 21L, LocalDate.of(2026, 3, 12)))
                .thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(parada));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L), 66L, 15L, LocalDate.of(2026, 3, 12)
        )).thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(viagem, List.of(parada), 21L))
                .thenReturn(null);

        var response = viagemOperacionalService.buscarViagemAtualDoPassageiro(21L, LocalDate.of(2026, 3, 12), 66L);

        assertThat(response).isPresent();
        assertThat(response.get().getRotaNome()).isEqualTo("Rota Escolar");
        assertThat(response.get().getServicoNome()).isEqualTo("Linha Manha Snapshot");
    }

    @Test
    void shouldCompleteTripWhenAllStopsLeavePendingState() {
        Viagem viagem = viagem(30L, 66L, 7L);
        ViagemParada parada = parada(300L, viagem, 1, passageiro(21L), ViagemParadaStatus.PENDING);

        when(viagemRepository.findByIdAndCompanyId(30L, 66L)).thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findByIdAndViagemIdAndCompanyId(300L, 30L, 66L)).thenReturn(Optional.of(parada));
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(parada));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(List.of(21L), 66L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(viagem, List.of(parada), null))
                .thenReturn(null);

        var response = viagemOperacionalService.atualizarStatusDaParada(30L, 300L, 66L, ViagemParadaStatus.COMPLETED);

        assertThat(response.getStatus()).isEqualTo(ViagemStatus.COMPLETED);
        assertThat(response.getParadas().get(0).getStatus()).isEqualTo(ViagemParadaStatus.COMPLETED);
        verify(viagemRealtimePublisherService).publishTripSnapshot(viagem, List.of(parada));
    }

    @Test
    void shouldMarkStopAsNoShowAndAdvanceTrip() {
        Viagem viagem = viagem(30L, 66L, 7L);
        ViagemParada primeira = parada(300L, viagem, 1, passageiro(21L), ViagemParadaStatus.PENDING);
        ViagemParada segunda = parada(301L, viagem, 2, passageiro(22L), ViagemParadaStatus.PENDING);

        when(viagemRepository.findByIdAndCompanyId(30L, 66L)).thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findByIdAndViagemIdAndCompanyId(300L, 30L, 66L)).thenReturn(Optional.of(primeira));
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(primeira, segunda));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(any(), eq(66L), eq(LocalDate.of(2026, 3, 12))))
                .thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(viagem, List.of(primeira, segunda), null))
                .thenReturn(null);

        var response = viagemOperacionalService.marcarNoShowParada(30L, 300L, 66L);

        assertThat(response.getStatus()).isEqualTo(ViagemStatus.IN_PROGRESS);
        assertThat(response.getParadas().get(0).getStatus()).isEqualTo(ViagemParadaStatus.NO_SHOW);
        assertThat(response.getProximaParada().getId()).isEqualTo(301L);
        verify(viagemRealtimePublisherService).publishTripSnapshot(viagem, List.of(primeira, segunda));
    }

    @Test
    void shouldStartTripThroughExplicitLifecycleAction() {
        Viagem viagem = viagem(30L, 66L, 7L);
        ViagemParada parada = parada(300L, viagem, 1, passageiro(21L), ViagemParadaStatus.PENDING);

        when(viagemRepository.findByIdAndCompanyId(30L, 66L)).thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L)).thenReturn(List.of(parada));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(List.of(21L), 66L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(viagem, List.of(parada), null))
                .thenReturn(null);

        var response = viagemOperacionalService.iniciarCorrida(30L, 66L);

        assertThat(response.getStatus()).isEqualTo(ViagemStatus.IN_PROGRESS);
        verify(viagemRotaGeograficaService).calcularEAplicarSnapshotOperacional(viagem, List.of(parada));
        verify(viagemRealtimePublisherService).publishTripSnapshot(viagem, List.of(parada));
    }

    @Test
    void shouldRejectStartTripWhenAlreadyCompleted() {
        Viagem viagem = viagem(30L, 66L, 7L);
        viagem.setStatus(ViagemStatus.COMPLETED);

        when(viagemRepository.findByIdAndCompanyId(30L, 66L)).thenReturn(Optional.of(viagem));

        assertThatThrownBy(() -> viagemOperacionalService.iniciarCorrida(30L, 66L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("encerrada");
    }

    @Test
    void shouldRejectChangingStopWhenTripAlreadyCompleted() {
        Viagem viagem = viagem(30L, 66L, 7L);
        viagem.setStatus(ViagemStatus.COMPLETED);
        ViagemParada parada = parada(300L, viagem, 1, passageiro(21L), ViagemParadaStatus.PENDING);

        when(viagemRepository.findByIdAndCompanyId(30L, 66L)).thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findByIdAndViagemIdAndCompanyId(300L, 30L, 66L)).thenReturn(Optional.of(parada));

        assertThatThrownBy(() -> viagemOperacionalService.concluirParada(30L, 300L, 66L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("encerrada");
    }

    @Test
    void shouldReorderTripStopsAndIncreaseRouteVersion() {
        Viagem viagem = viagem(30L, 66L, 7L);
        viagem.setRotaVersao(1);
        ViagemParada primeira = parada(300L, viagem, 1, passageiro(21L), ViagemParadaStatus.PENDING);
        ViagemParada segunda = parada(301L, viagem, 2, passageiro(22L), ViagemParadaStatus.PENDING);

        when(viagemRepository.findByIdAndCompanyId(30L, 66L)).thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(30L))
                .thenReturn(List.of(primeira, segunda), List.of(segunda, primeira));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(any(), eq(66L), eq(LocalDate.of(2026, 3, 12))))
                .thenReturn(List.of());
        when(viagemRotaGeograficaService.buildForTrip(any(Viagem.class), any(), isNull()))
                .thenReturn(null);

        var response = viagemOperacionalService.atualizarRotaDaViagem(30L, 66L, List.of(301L, 300L));

        assertThat(response.getRotaVersao()).isEqualTo(2);
        assertThat(response.getParadas()).extracting("id").containsExactly(301L, 300L);
        verify(messagingTemplate).convertAndSend(eq("/topic/viagens/66"), any(Object.class));
        verify(viagemRealtimePublisherService).publishTripSnapshot(any(Viagem.class), eq(List.of(segunda, primeira)));
    }

    @Test
    void shouldRejectRouteReorderAfterTripStarted() {
        Viagem viagem = viagem(30L, 66L, 7L);
        viagem.setStatus(ViagemStatus.IN_PROGRESS);
        viagem.setRotaVersao(1);

        when(viagemRepository.findByIdAndCompanyId(30L, 66L)).thenReturn(Optional.of(viagem));

        assertThatThrownBy(() -> viagemOperacionalService.atualizarRotaDaViagem(30L, 66L, List.of(301L, 300L)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("A rota só pode ser editada antes de iniciar a corrida.");
    }

    private Viagem viagem(Long id, Long companyId, Long motoristaId) {
        Company company = new Company("VanRides Default", "default");
        company.setId(companyId);

        Motorista motorista = new Motorista();
        motorista.setId(motoristaId);
        motorista.setNome("Motorista");
        motorista.setEmail("motorista@vanrides.dev");
        motorista.setTelefone("51999990000");

        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(10L);
        rotaBase.setNome("Rota Escolar");
        rotaBase.setCompany(company);
        rotaBase.setMotorista(motorista);

        Viagem viagem = new Viagem();
        viagem.setId(id);
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setRotaBase(rotaBase);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));
        viagem.setStatus(ViagemStatus.PLANNED);
        return viagem;
    }

    private ViagemParada parada(Long id,
                                Viagem viagem,
                                int ordem,
                                Passageiro passageiro,
                                ViagemParadaStatus status) {
        ViagemParada parada = new ViagemParada();
        parada.setId(id);
        parada.setViagem(viagem);
        parada.setOrdem(ordem);
        parada.setPassageiro(passageiro);
        parada.setStatus(status);
        return parada;
    }

    private Passageiro passageiro(Long id) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro");
        passageiro.setEmail("passageiro@vanrides.dev");
        passageiro.setTelefone("51999990001");
        return passageiro;
    }

    private ServicoRecorrente servico(Long id, Company company, String nome) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company);
        servico.setNome(nome);
        return servico;
    }
}
