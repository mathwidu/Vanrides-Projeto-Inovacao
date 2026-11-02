package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.Presenca;
import com.vanrides.vanrides.model.PresencaServico;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.RotaBaseParada;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PresencaRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.RotaBaseParadaRepository;
import com.vanrides.vanrides.repository.RotaBaseRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViagemProvisioningServiceTest {

    @Mock private ViagemRepository viagemRepository;
    @Mock private ViagemParadaRepository viagemParadaRepository;
    @Mock private RotaBaseRepository rotaBaseRepository;
    @Mock private RotaBaseParadaRepository rotaBaseParadaRepository;
    @Mock private ServicoRecorrenteRepository servicoRecorrenteRepository;
    @Mock private ServicoPassageiroRepository servicoPassageiroRepository;
    @Mock private PresencaServicoRepository presencaServicoRepository;
    @Mock private MotoristaRepository motoristaRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private PresencaRepository presencaRepository;
    @Mock private ViagemEtaCalculator viagemEtaCalculator;
    @Spy private ServicoTripPolicy servicoTripPolicy = new ServicoTripPolicy();
    @Spy private ViagemSnapshotApplier viagemSnapshotApplier = new ViagemSnapshotApplier();
    @Mock private CompanyTripManifestMaintenanceService companyTripManifestMaintenanceService;

    @InjectMocks
    private ViagemProvisioningService viagemProvisioningService;

    @Test
    void shouldCreateAutomaticTripWithAllBaseStopsWhenNoPresenceExists() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        RotaBase rotaBase = rotaBase(10L, company, motorista);
        Passageiro alunoA = passageiro(21L);
        Passageiro alunoB = passageiro(22L);
        RotaBaseParada paradaA = paradaBase(rotaBase, 1, alunoA);
        RotaBaseParada paradaB = paradaBase(rotaBase, 2, alunoB);

        when(viagemRepository.findByMotoristaIdAndDataOperacaoAndCompanyId(7L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.findAllAutomaticosByMotoristaPadraoIdAndCompanyId(7L, 66L))
                .thenReturn(List.of());
        when(rotaBaseRepository.findAllAutomaticasByMotoristaIdAndCompanyId(7L, 66L))
                .thenReturn(List.of(rotaBase));
        when(servicoRecorrenteRepository.findByRotaBaseLegadaIdAndCompanyId(10L, 66L))
                .thenReturn(Optional.empty());
        when(viagemRepository.existsByRotaBaseIdAndDataOperacaoAndCompanyId(10L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(false);
        when(rotaBaseParadaRepository.findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(10L))
                .thenReturn(List.of(paradaA, paradaB));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(List.of(21L, 22L), 66L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of());
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> {
            Viagem viagem = invocation.getArgument(0);
            if (viagem.getId() == null) {
                viagem.setId(30L);
            }
            return viagem;
        });
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var viagem = viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(66L, 7L, LocalDate.of(2026, 3, 19));

        ArgumentCaptor<ViagemParada> paradaCaptor = ArgumentCaptor.forClass(ViagemParada.class);
        verify(viagemParadaRepository, org.mockito.Mockito.times(2)).save(paradaCaptor.capture());

        assertThat(viagem).isPresent();
        assertThat(viagem.get().getId()).isEqualTo(30L);
        assertThat(paradaCaptor.getAllValues()).extracting(ViagemParada::getOrdem).containsExactly(1, 2);
        assertThat(paradaCaptor.getAllValues()).extracting(item -> item.getPassageiro().getId()).containsExactly(21L, 22L);
    }

    @Test
    void shouldCreateAutomaticTripOnlyWithConfirmedPassengersWhenPresenceExists() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        RotaBase rotaBase = rotaBase(10L, company, motorista);
        Passageiro alunoA = passageiro(21L);
        Passageiro alunoB = passageiro(22L);
        RotaBaseParada paradaA = paradaBase(rotaBase, 1, alunoA);
        RotaBaseParada paradaB = paradaBase(rotaBase, 2, alunoB);
        Presenca presencaConfirmada = presenca(company, alunoA, LocalDate.of(2026, 3, 19), true);
        Presenca presencaNaoConfirmada = presenca(company, alunoB, LocalDate.of(2026, 3, 19), false);

        when(viagemRepository.findByPassageiroIdAndDataOperacaoAndCompanyId(21L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.findAllAutomaticosByPassageiroIdAndCompanyId(21L, 66L))
                .thenReturn(List.of());
        when(rotaBaseRepository.findAllAutomaticasByPassageiroIdAndCompanyId(21L, 66L))
                .thenReturn(List.of(rotaBase));
        when(servicoRecorrenteRepository.findByRotaBaseLegadaIdAndCompanyId(10L, 66L))
                .thenReturn(Optional.empty());
        when(viagemRepository.existsByRotaBaseIdAndDataOperacaoAndCompanyId(10L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(false);
        when(rotaBaseParadaRepository.findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(10L))
                .thenReturn(List.of(paradaA, paradaB));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(List.of(21L, 22L), 66L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of(presencaConfirmada, presencaNaoConfirmada));
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> {
            Viagem viagem = invocation.getArgument(0);
            if (viagem.getId() == null) {
                viagem.setId(31L);
            }
            return viagem;
        });
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var viagem = viagemProvisioningService.buscarOuCriarViagemAutomaticaDoPassageiro(66L, 21L, LocalDate.of(2026, 3, 19));

        ArgumentCaptor<ViagemParada> paradaCaptor = ArgumentCaptor.forClass(ViagemParada.class);
        verify(viagemParadaRepository).save(paradaCaptor.capture());

        assertThat(viagem).isPresent();
        assertThat(viagem.get().getId()).isEqualTo(31L);
        assertThat(paradaCaptor.getValue().getPassageiro().getId()).isEqualTo(21L);
    }

    @Test
    void shouldSkipAutomaticTripWhenPresenceExistsButNobodyConfirmed() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        RotaBase rotaBase = rotaBase(10L, company, motorista);
        Passageiro aluno = passageiro(21L);
        RotaBaseParada parada = paradaBase(rotaBase, 1, aluno);
        Presenca presenca = presenca(company, aluno, LocalDate.of(2026, 3, 19), false);

        when(viagemRepository.findByMotoristaIdAndDataOperacaoAndCompanyId(7L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.findAllAutomaticosByMotoristaPadraoIdAndCompanyId(7L, 66L))
                .thenReturn(List.of());
        when(rotaBaseRepository.findAllAutomaticasByMotoristaIdAndCompanyId(7L, 66L))
                .thenReturn(List.of(rotaBase));
        when(servicoRecorrenteRepository.findByRotaBaseLegadaIdAndCompanyId(10L, 66L))
                .thenReturn(Optional.empty());
        when(viagemRepository.existsByRotaBaseIdAndDataOperacaoAndCompanyId(10L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(false);
        when(rotaBaseParadaRepository.findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(10L))
                .thenReturn(List.of(parada));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(List.of(21L), 66L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of(presenca));

        var viagem = viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(66L, 7L, LocalDate.of(2026, 3, 19));

        assertThat(viagem).isEmpty();
    }

    @Test
    void shouldCreateAutomaticTripFromRecurringServiceUsingDefaultAttendancePolicy() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(15L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        Passageiro alunoA = passageiro(21L);
        Passageiro alunoB = passageiro(22L);
        ServicoPassageiro paradaA = servicoPassageiro(servico, alunoA, 1);
        ServicoPassageiro paradaB = servicoPassageiro(servico, alunoB, 2);
        PresencaServico ausenciaA = presencaServico(company, servico, alunoA, LocalDate.of(2026, 3, 19), PresencaServicoStatus.ABSENT);

        when(viagemRepository.findByMotoristaIdAndDataOperacaoAndCompanyId(7L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.findAllAutomaticosByMotoristaPadraoIdAndCompanyId(7L, 66L))
                .thenReturn(List.of(servico));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(15L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(15L, 66L))
                .thenReturn(List.of(paradaA, paradaB));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L, 22L), 66L, 15L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of(ausenciaA));
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> {
            Viagem viagem = invocation.getArgument(0);
            if (viagem.getId() == null) {
                viagem.setId(40L);
            }
            return viagem;
        });
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var viagem = viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(66L, 7L, LocalDate.of(2026, 3, 19));

        ArgumentCaptor<ViagemParada> paradaCaptor = ArgumentCaptor.forClass(ViagemParada.class);
        verify(viagemParadaRepository).save(paradaCaptor.capture());

        assertThat(viagem).isPresent();
        assertThat(viagem.get().getServicoRecorrente().getId()).isEqualTo(15L);
        assertThat(viagem.get().getMotorista().getId()).isEqualTo(7L);
        assertThat(viagem.get().getServicoNomeSnapshot()).isEqualTo("Serviço 15");
        assertThat(paradaCaptor.getValue().getPassageiro().getId()).isEqualTo(22L);
    }

    @Test
    void shouldUseMigratedRecurringServiceInsteadOfLegacyRouteFallbackForDriverAgenda() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        RotaBase rotaBaseLegada = rotaBase(10L, company, motorista);
        ServicoRecorrente servico = servico(15L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        servico.setNome("Linha Centro Manhã");
        servico.setRotaBaseLegada(rotaBaseLegada);
        Passageiro aluno = passageiro(21L);
        ServicoPassageiro parada = servicoPassageiro(servico, aluno, 1);

        when(viagemRepository.findByMotoristaIdAndDataOperacaoAndCompanyId(7L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.findAllAutomaticosByMotoristaPadraoIdAndCompanyId(7L, 66L))
                .thenReturn(List.of(servico));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(15L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(15L, 66L))
                .thenReturn(List.of(parada));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L), 66L, 15L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of());
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> {
            Viagem viagem = invocation.getArgument(0);
            if (viagem.getId() == null) {
                viagem.setId(40L);
            }
            return viagem;
        });
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rotaBaseRepository.findAllAutomaticasByMotoristaIdAndCompanyId(7L, 66L))
                .thenReturn(List.of(rotaBaseLegada));
        when(servicoRecorrenteRepository.findByRotaBaseLegadaIdAndCompanyId(10L, 66L))
                .thenReturn(Optional.of(servico));

        var viagens = viagemProvisioningService.buscarOuCriarViagensAutomaticasDoMotorista(66L, 7L, LocalDate.of(2026, 3, 19));

        assertThat(viagens).hasSize(1);
        assertThat(viagens.get(0).getServicoRecorrente().getId()).isEqualTo(15L);
        assertThat(viagens.get(0).getRotaBase().getId()).isEqualTo(10L);
        assertThat(viagens.get(0).getServicoNomeSnapshot()).isEqualTo("Linha Centro Manhã");
        verify(rotaBaseParadaRepository, never()).findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(10L);
    }

    @Test
    void shouldAttachManualLegacyRouteTripToMigratedRecurringServiceSnapshot() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        RotaBase rotaBaseLegada = rotaBase(10L, company, motorista);
        rotaBaseLegada.setNome("Rota Legada");
        ServicoRecorrente servico = servico(15L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        servico.setNome("Linha Operacional");
        servico.setTipoOperacao(TipoOperacaoServico.VOLTA);
        servico.setDestinoFinalNome("Terminal Operacional");
        servico.setRotaBaseLegada(rotaBaseLegada);
        Passageiro aluno = passageiro(21L);
        RotaBaseParada parada = paradaBase(rotaBaseLegada, 1, aluno);

        when(rotaBaseRepository.findByIdAndCompanyId(10L, 66L)).thenReturn(Optional.of(rotaBaseLegada));
        when(viagemRepository.existsByRotaBaseIdAndDataOperacaoAndCompanyId(10L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(false);
        when(servicoRecorrenteRepository.findByRotaBaseLegadaIdAndCompanyId(10L, 66L))
                .thenReturn(Optional.of(servico));
        when(viagemRepository.existsByServicoRecorrenteIdAndDataOperacaoAndCompanyId(15L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(false);
        when(rotaBaseParadaRepository.findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(10L))
                .thenReturn(List.of(parada));
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> {
            Viagem viagem = invocation.getArgument(0);
            if (viagem.getId() == null) {
                viagem.setId(41L);
            }
            return viagem;
        });
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Viagem viagem = viagemProvisioningService.criarViagemManual(66L, 10L, LocalDate.of(2026, 3, 19));

        assertThat(viagem.getId()).isEqualTo(41L);
        assertThat(viagem.getRotaBase().getId()).isEqualTo(10L);
        assertThat(viagem.getServicoRecorrente().getId()).isEqualTo(15L);
        assertThat(viagem.getMotorista().getId()).isEqualTo(7L);
        assertThat(viagem.getServicoNomeSnapshot()).isEqualTo("Linha Operacional");
        assertThat(viagem.getTipoOperacaoSnapshot()).isEqualTo(TipoOperacaoServico.VOLTA);
        assertThat(viagem.getDestinoFinalNomeSnapshot()).isEqualTo("Terminal Operacional");
    }

    @Test
    void shouldCreateServiceTripOnlyWithConfirmedPassengersWhenPolicyRequiresConfirmation() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(16L, company, motorista, PoliticaPresencaServico.EXIGE_CONFIRMACAO);
        Passageiro alunoA = passageiro(21L);
        Passageiro alunoB = passageiro(22L);
        ServicoPassageiro paradaA = servicoPassageiro(servico, alunoA, 1);
        ServicoPassageiro paradaB = servicoPassageiro(servico, alunoB, 2);
        PresencaServico confirmacaoA = presencaServico(company, servico, alunoA, LocalDate.of(2026, 3, 19), PresencaServicoStatus.CONFIRMED);

        when(viagemRepository.findByMotoristaIdAndDataOperacaoAndCompanyId(7L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.findAllAutomaticosByMotoristaPadraoIdAndCompanyId(7L, 66L))
                .thenReturn(List.of(servico));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(16L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(16L, 66L))
                .thenReturn(List.of(paradaA, paradaB));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L, 22L), 66L, 16L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of(confirmacaoA));
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> {
            Viagem viagem = invocation.getArgument(0);
            if (viagem.getId() == null) {
                viagem.setId(41L);
            }
            return viagem;
        });
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var viagem = viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(66L, 7L, LocalDate.of(2026, 3, 19));

        ArgumentCaptor<ViagemParada> paradaCaptor = ArgumentCaptor.forClass(ViagemParada.class);
        verify(viagemParadaRepository).save(paradaCaptor.capture());

        assertThat(viagem).isPresent();
        assertThat(paradaCaptor.getValue().getPassageiro().getId()).isEqualTo(21L);
    }

    @Test
    void shouldGenerateOnlySelectedDailyTripsWhenPassengerHasMultipleServices() {
        LocalDate dataOperacao = LocalDate.of(2026, 5, 25);
        Company company = company(66L);
        Motorista motoristaManha = motorista(7L);
        Motorista motoristaTarde = motorista(8L);
        Motorista motoristaNoite = motorista(9L);
        Passageiro passageiro = passageiro(21L);

        ServicoRecorrente manha = servico(31L, company, motoristaManha, PoliticaPresencaServico.EXIGE_CONFIRMACAO);
        manha.setNome("Linha Manhã");
        manha.setHorarioPartida(LocalTime.of(7, 0));
        ServicoRecorrente tarde = servico(32L, company, motoristaTarde, PoliticaPresencaServico.EXIGE_CONFIRMACAO);
        tarde.setNome("Linha Tarde");
        tarde.setHorarioPartida(LocalTime.of(13, 0));
        ServicoRecorrente noite = servico(33L, company, motoristaNoite, PoliticaPresencaServico.EXIGE_CONFIRMACAO);
        noite.setNome("Linha Noite");
        noite.setHorarioPartida(LocalTime.of(18, 0));

        when(servicoRecorrenteRepository.findAllAutomaticosByCompanyId(66L))
                .thenReturn(List.of(manha, tarde, noite));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(31L, dataOperacao, 66L))
                .thenReturn(Optional.empty());
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(32L, dataOperacao, 66L))
                .thenReturn(Optional.empty());
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(33L, dataOperacao, 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(31L, 66L))
                .thenReturn(List.of(servicoPassageiro(manha, passageiro, 1)));
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(32L, 66L))
                .thenReturn(List.of(servicoPassageiro(tarde, passageiro, 1)));
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(33L, 66L))
                .thenReturn(List.of(servicoPassageiro(noite, passageiro, 1)));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L), 66L, 31L, dataOperacao))
                .thenReturn(List.of(presencaServico(company, manha, passageiro, dataOperacao, PresencaServicoStatus.CONFIRMED)));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L), 66L, 32L, dataOperacao))
                .thenReturn(List.of(presencaServico(company, tarde, passageiro, dataOperacao, PresencaServicoStatus.ABSENT)));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L), 66L, 33L, dataOperacao))
                .thenReturn(List.of(presencaServico(company, noite, passageiro, dataOperacao, PresencaServicoStatus.CONFIRMED)));
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        AtomicLong tripId = new AtomicLong(100L);
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> {
            Viagem viagem = invocation.getArgument(0);
            if (viagem.getId() == null) {
                viagem.setId(tripId.getAndIncrement());
            }
            return viagem;
        });
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = viagemProvisioningService.gerarViagensAutomaticasDaEmpresa(66L, dataOperacao);

        ArgumentCaptor<ViagemParada> paradaCaptor = ArgumentCaptor.forClass(ViagemParada.class);
        verify(viagemParadaRepository, org.mockito.Mockito.times(2)).save(paradaCaptor.capture());
        assertThat(result.criadas()).isEqualTo(2);
        assertThat(result.ignoradas()).isEqualTo(1);
        assertThat(result.viagens())
                .extracting(Viagem::getServicoNomeSnapshot, Viagem::getHorarioPartida)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Linha Manhã", LocalTime.of(7, 0)),
                        org.assertj.core.groups.Tuple.tuple("Linha Noite", LocalTime.of(18, 0))
                );
        assertThat(result.viagens())
                .extracting(viagem -> viagem.getMotorista().getId())
                .containsExactly(7L, 9L);
        assertThat(result.servicosIgnorados())
                .extracting(
                        ViagemProvisioningService.ServicoIgnorado::servicoId,
                        ViagemProvisioningService.ServicoIgnorado::motivo
                )
                .containsExactly(org.assertj.core.groups.Tuple.tuple(
                        32L,
                        ViagemProvisioningService.MotivoServicoIgnorado.SEM_PASSAGEIROS_OPERACIONAIS
                ));
        assertThat(paradaCaptor.getAllValues())
                .extracting(parada -> parada.getPassageiro().getId())
                .containsExactly(21L, 21L);
    }

    @Test
    void shouldRejectManualServiceTripWhenManifestExceedsPlannedCapacity() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(16L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        servico.setCapacidadePlanejada(1);
        Passageiro alunoA = passageiro(21L);
        Passageiro alunoB = passageiro(22L);
        ServicoPassageiro paradaA = servicoPassageiro(servico, alunoA, 1);
        ServicoPassageiro paradaB = servicoPassageiro(servico, alunoB, 2);

        when(servicoRecorrenteRepository.findByIdAndCompanyId(16L, 66L))
                .thenReturn(Optional.of(servico));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(16L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(16L, 66L))
                .thenReturn(List.of(paradaA, paradaB));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L, 22L), 66L, 16L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of());
        lenient().when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        lenient().when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Throwable thrown = catchThrowable(() ->
                viagemProvisioningService.criarViagemManual(66L, null, 16L, null, LocalDate.of(2026, 3, 19)));

        assertThat(thrown).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) thrown).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(thrown).hasMessageContaining("capacidade planejada");
        verify(viagemRepository, never()).save(any(Viagem.class));
    }

    @Test
    void shouldIgnoreAutomaticServiceTripWhenManifestExceedsPlannedCapacity() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(17L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        servico.setCapacidadePlanejada(1);
        Passageiro alunoA = passageiro(21L);
        Passageiro alunoB = passageiro(22L);
        ServicoPassageiro paradaA = servicoPassageiro(servico, alunoA, 1);
        ServicoPassageiro paradaB = servicoPassageiro(servico, alunoB, 2);

        when(servicoRecorrenteRepository.findAllAutomaticosByCompanyId(66L))
                .thenReturn(List.of(servico));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(17L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(17L, 66L))
                .thenReturn(List.of(paradaA, paradaB));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L, 22L), 66L, 17L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of());
        lenient().when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        lenient().when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = viagemProvisioningService.gerarViagensAutomaticasDaEmpresa(66L, LocalDate.of(2026, 3, 19));

        assertThat(result.criadas()).isZero();
        assertThat(result.ignoradas()).isEqualTo(1);
        assertThat(result.viagens()).isEmpty();
        verify(viagemRepository, never()).save(any(Viagem.class));
    }

    @Test
    void shouldReportWhyCompanyAutomaticTripsWereIgnored() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente foraDaData = servico(21L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        foraDaData.setNome("Linha Segunda");
        foraDaData.setDiasOperacao("MONDAY");
        ServicoRecorrente semMotorista = servico(22L, company, null, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        semMotorista.setNome("Linha Sem Motorista");
        ServicoRecorrente semPassageiros = servico(23L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        semPassageiros.setNome("Linha Vazia");
        ServicoRecorrente lotado = servico(24L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        lotado.setNome("Linha Lotada");
        lotado.setCapacidadePlanejada(1);
        Passageiro alunoA = passageiro(31L);
        Passageiro alunoB = passageiro(32L);

        when(servicoRecorrenteRepository.findAllAutomaticosByCompanyId(66L))
                .thenReturn(List.of(foraDaData, semMotorista, semPassageiros, lotado));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(22L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(23L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(24L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(23L, 66L))
                .thenReturn(List.of());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(24L, 66L))
                .thenReturn(List.of(
                        servicoPassageiro(lotado, alunoA, 1),
                        servicoPassageiro(lotado, alunoB, 2)
                ));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(31L, 32L), 66L, 24L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of());

        var result = viagemProvisioningService.gerarViagensAutomaticasDaEmpresa(66L, LocalDate.of(2026, 3, 19));

        assertThat(result.criadas()).isZero();
        assertThat(result.existentes()).isZero();
        assertThat(result.ignoradas()).isEqualTo(4);
        assertThat(result.servicosIgnorados())
                .extracting(
                        ViagemProvisioningService.ServicoIgnorado::servicoId,
                        ViagemProvisioningService.ServicoIgnorado::servicoNome,
                        ViagemProvisioningService.ServicoIgnorado::motivo
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(21L, "Linha Segunda", ViagemProvisioningService.MotivoServicoIgnorado.FORA_DA_DATA),
                        org.assertj.core.groups.Tuple.tuple(22L, "Linha Sem Motorista", ViagemProvisioningService.MotivoServicoIgnorado.SEM_MOTORISTA),
                        org.assertj.core.groups.Tuple.tuple(23L, "Linha Vazia", ViagemProvisioningService.MotivoServicoIgnorado.SEM_PASSAGEIROS_OPERACIONAIS),
                        org.assertj.core.groups.Tuple.tuple(24L, "Linha Lotada", ViagemProvisioningService.MotivoServicoIgnorado.CAPACIDADE_EXCEDIDA)
                );
        assertThat(result.servicosIgnorados())
                .extracting(ViagemProvisioningService.ServicoIgnorado::mensagem)
                .allSatisfy(mensagem -> assertThat(mensagem).isNotBlank());
        verify(viagemRepository, never()).save(any(Viagem.class));
    }

    @Test
    void shouldGenerateCompanyAutomaticTripsIdempotentlyForOperationDate() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servicoExistente = servico(15L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        ServicoRecorrente servicoNovo = servico(16L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        Passageiro aluno = passageiro(21L);
        ServicoPassageiro parada = servicoPassageiro(servicoNovo, aluno, 1);
        Viagem viagemExistente = new Viagem();
        viagemExistente.setId(39L);
        viagemExistente.setCompany(company);
        viagemExistente.setServicoRecorrente(servicoExistente);
        viagemExistente.setMotorista(motorista);
        viagemExistente.setDataOperacao(LocalDate.of(2026, 3, 19));
        viagemExistente.setStatus(ViagemStatus.PLANNED);

        when(servicoRecorrenteRepository.findAllAutomaticosByCompanyId(66L))
                .thenReturn(List.of(servicoExistente, servicoNovo));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(15L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.of(viagemExistente));
        when(companyTripManifestMaintenanceService.maintain(66L, servicoExistente, viagemExistente))
                .thenReturn(CompanyTripManifestMaintenanceService.ExistingTripMaintenance.existente(viagemExistente));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(16L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(16L, 66L))
                .thenReturn(List.of(parada));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L), 66L, 16L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of());
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> {
            Viagem viagem = invocation.getArgument(0);
            if (viagem.getId() == null) {
                viagem.setId(40L);
            }
            return viagem;
        });
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = viagemProvisioningService.gerarViagensAutomaticasDaEmpresa(66L, LocalDate.of(2026, 3, 19));

        assertThat(result.criadas()).isEqualTo(1);
        assertThat(result.existentes()).isEqualTo(1);
        assertThat(result.atualizadas()).isZero();
        assertThat(result.bloqueadas()).isZero();
        assertThat(result.ignoradas()).isZero();
        assertThat(result.viagens()).extracting(Viagem::getId).containsExactly(39L, 40L);
    }

    @Test
    void shouldBuildDriverDayAgendaWithExistingAndCreatedTrips() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(16L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        Passageiro aluno = passageiro(21L);
        ServicoPassageiro parada = servicoPassageiro(servico, aluno, 1);
        Viagem existente = new Viagem();
        existente.setId(39L);
        existente.setCompany(company);
        existente.setMotorista(motorista);
        existente.setDataOperacao(LocalDate.of(2026, 3, 19));

        when(viagemRepository.findByMotoristaIdAndDataOperacaoAndCompanyId(7L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of(existente));
        when(servicoRecorrenteRepository.findAllAutomaticosByMotoristaPadraoIdAndCompanyId(7L, 66L))
                .thenReturn(List.of(servico));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(16L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.empty());
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(16L, 66L))
                .thenReturn(List.of(parada));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L), 66L, 16L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of());
        when(companyRepository.findById(66L)).thenReturn(Optional.of(company));
        when(viagemRepository.save(any(Viagem.class))).thenAnswer(invocation -> {
            Viagem viagem = invocation.getArgument(0);
            if (viagem.getId() == null) {
                viagem.setId(40L);
            }
            return viagem;
        });
        when(viagemParadaRepository.save(any(ViagemParada.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(viagemParadaRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var viagens = viagemProvisioningService.buscarOuCriarViagensAutomaticasDoMotorista(66L, 7L, LocalDate.of(2026, 3, 19));

        assertThat(viagens).extracting(Viagem::getId).containsExactly(39L, 40L);
    }

    @Test
    void shouldNotShowExistingServiceTripToDefaultDriverAfterOperationalDriverChange() {
        Company company = company(66L);
        Motorista motoristaPadrao = motorista(7L);
        Motorista motoristaOperacional = motorista(8L);
        ServicoRecorrente servico = servico(16L, company, motoristaPadrao, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        Viagem existenteComOutroMotorista = new Viagem();
        existenteComOutroMotorista.setId(40L);
        existenteComOutroMotorista.setCompany(company);
        existenteComOutroMotorista.setServicoRecorrente(servico);
        existenteComOutroMotorista.setMotorista(motoristaOperacional);
        existenteComOutroMotorista.setDataOperacao(LocalDate.of(2026, 3, 19));

        when(viagemRepository.findByMotoristaIdAndDataOperacaoAndCompanyId(7L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.findAllAutomaticosByMotoristaPadraoIdAndCompanyId(7L, 66L))
                .thenReturn(List.of(servico));
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(16L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.of(existenteComOutroMotorista));

        var viagens = viagemProvisioningService.buscarOuCriarViagensAutomaticasDoMotorista(66L, 7L, LocalDate.of(2026, 3, 19));

        assertThat(viagens).isEmpty();
    }

    @Test
    void shouldNotShowExistingServiceTripToPassengerWhenSnapshotDoesNotContainPassenger() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(16L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        Passageiro passageiroAlvo = passageiro(21L);
        ServicoPassageiro paradaAlvo = servicoPassageiro(servico, passageiroAlvo, 1);
        Viagem existenteSemPassageiro = new Viagem();
        existenteSemPassageiro.setId(40L);
        existenteSemPassageiro.setCompany(company);
        existenteSemPassageiro.setServicoRecorrente(servico);
        existenteSemPassageiro.setMotorista(motorista);
        existenteSemPassageiro.setDataOperacao(LocalDate.of(2026, 3, 19));
        ViagemParada paradaDeOutroPassageiro = new ViagemParada();
        paradaDeOutroPassageiro.setPassageiro(passageiro(22L));

        when(viagemRepository.findByPassageiroIdAndDataOperacaoAndCompanyId(21L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.findAllAutomaticosByPassageiroIdAndCompanyId(21L, 66L))
                .thenReturn(List.of(servico));
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(16L, 66L))
                .thenReturn(List.of(paradaAlvo));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L), 66L, 16L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of());
        when(viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(16L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(Optional.of(existenteSemPassageiro));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(40L))
                .thenReturn(List.of(paradaDeOutroPassageiro));

        var viagens = viagemProvisioningService.buscarOuCriarViagensAutomaticasDoPassageiro(66L, 21L, LocalDate.of(2026, 3, 19));

        assertThat(viagens).isEmpty();
    }

    @Test
    void shouldNotShowNewServiceTripToPassengerWhenPresenceRemovesPassengerFromManifest() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        ServicoRecorrente servico = servico(16L, company, motorista, PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        Passageiro alunoAusente = passageiro(21L);
        Passageiro alunoPresente = passageiro(22L);
        ServicoPassageiro paradaAusente = servicoPassageiro(servico, alunoAusente, 1);
        ServicoPassageiro paradaPresente = servicoPassageiro(servico, alunoPresente, 2);
        PresencaServico ausencia = presencaServico(company, servico, alunoAusente, LocalDate.of(2026, 3, 19), PresencaServicoStatus.ABSENT);

        when(viagemRepository.findByPassageiroIdAndDataOperacaoAndCompanyId(21L, LocalDate.of(2026, 3, 19), 66L))
                .thenReturn(List.of());
        when(servicoRecorrenteRepository.findAllAutomaticosByPassageiroIdAndCompanyId(21L, 66L))
                .thenReturn(List.of(servico));
        when(servicoPassageiroRepository.findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(16L, 66L))
                .thenReturn(List.of(paradaAusente, paradaPresente));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L, 22L), 66L, 16L, LocalDate.of(2026, 3, 19)))
                .thenReturn(List.of(ausencia));

        var viagens = viagemProvisioningService.buscarOuCriarViagensAutomaticasDoPassageiro(66L, 21L, LocalDate.of(2026, 3, 19));

        assertThat(viagens).isEmpty();
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
        motorista.setEmail("motorista" + id + "@vanrides.dev");
        motorista.setTelefone("51999990000");
        return motorista;
    }

    private Passageiro passageiro(Long id) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro " + id);
        passageiro.setEmail("passageiro" + id + "@vanrides.dev");
        passageiro.setTelefone("51999990001");
        return passageiro;
    }

    private RotaBase rotaBase(Long id, Company company, Motorista motorista) {
        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(id);
        rotaBase.setCompany(company);
        rotaBase.setMotorista(motorista);
        rotaBase.setNome("Rota Escolar");
        rotaBase.setAtivo(true);
        return rotaBase;
    }

    private RotaBaseParada paradaBase(RotaBase rotaBase, int ordem, Passageiro passageiro) {
        RotaBaseParada parada = new RotaBaseParada();
        parada.setRotaBase(rotaBase);
        parada.setOrdem(ordem);
        parada.setPassageiro(passageiro);
        return parada;
    }

    private Presenca presenca(Company company, Passageiro passageiro, LocalDate data, boolean confirmado) {
        Presenca presenca = new Presenca(passageiro, data, confirmado);
        presenca.setId(200L + passageiro.getId());
        return presenca;
    }

    private ServicoRecorrente servico(Long id, Company company, Motorista motorista, PoliticaPresencaServico politicaPresenca) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company);
        servico.setMotoristaPadrao(motorista);
        servico.setNome("Serviço " + id);
        servico.setPoliticaPresenca(politicaPresenca);
        servico.setAtivo(true);
        servico.setGeracaoAutomaticaAtiva(true);
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

    private ViagemParada viagemParada(Long id, Viagem viagem, Passageiro passageiro, int ordem) {
        ViagemParada parada = new ViagemParada();
        parada.setId(id);
        parada.setViagem(viagem);
        parada.setPassageiro(passageiro);
        parada.setOrdem(ordem);
        return parada;
    }

    private PresencaServico presencaServico(Company company,
                                            ServicoRecorrente servico,
                                            Passageiro passageiro,
                                            LocalDate data,
                                            PresencaServicoStatus status) {
        PresencaServico presenca = new PresencaServico();
        presenca.setCompany(company);
        presenca.setServicoRecorrente(servico);
        presenca.setPassageiro(passageiro);
        presenca.setDataOperacao(data);
        presenca.setStatus(status);
        return presenca;
    }

}
