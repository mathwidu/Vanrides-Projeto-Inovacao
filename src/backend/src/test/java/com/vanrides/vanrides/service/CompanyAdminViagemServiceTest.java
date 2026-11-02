package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminViagemCreateRequest;
import com.vanrides.vanrides.dto.ViagemNavegacaoResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.dto.ViagemRotaLegResponse;
import com.vanrides.vanrides.dto.ViagemRotaWaypointResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.PresencaServico;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PresencaRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminViagemServiceTest {

    @Mock
    private ViagemRepository viagemRepository;
    @Mock
    private ViagemParadaRepository viagemParadaRepository;
    @Mock
    private ViagemProvisioningService viagemProvisioningService;
    @Mock
    private ViagemManifestSyncService viagemManifestSyncService;
    @Mock
    private AdminViagemManifestStatusResolver manifestStatusResolver;
    @Mock
    private MotoristaRepository motoristaRepository;
    @Mock
    private PresencaRepository presencaRepository;
    @Mock
    private PresencaServicoRepository presencaServicoRepository;
    @Mock
    private ViagemEtaCalculator viagemEtaCalculator;
    @Mock
    private ViagemRealtimePublisherService viagemRealtimePublisherService;
    @Mock
    private ViagemRotaGeograficaService viagemRotaGeograficaService;
    @Mock
    private ViagemTrackingStateService viagemTrackingStateService;

    @InjectMocks
    private CompanyAdminViagemService companyAdminViagemService;

    @Test
    void shouldCreateTripFromRouteTemplate() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        RotaBase rotaBase = rotaBase(10L, company, motorista);
        Viagem viagem = new Viagem();
        viagem.setId(30L);
        viagem.setCompany(company);
        viagem.setRotaBase(rotaBase);
        viagem.setMotorista(motorista);

        when(viagemProvisioningService.criarViagemManual(66L, 10L, null, null, LocalDate.of(2026, 3, 12)))
                .thenReturn(viagem);
        when(viagemParadaRepository.countByViagemId(30L)).thenReturn(2L);

        AdminViagemCreateRequest request = new AdminViagemCreateRequest();
        request.setRotaBaseId(10L);
        request.setDataOperacao(LocalDate.of(2026, 3, 12));

        var response = companyAdminViagemService.criarViagem(66L, request);

        assertThat(response.getId()).isEqualTo(30L);
        assertThat(response.getQuantidadeParadas()).isEqualTo(2);
        verify(viagemProvisioningService).criarViagemManual(66L, 10L, null, null, LocalDate.of(2026, 3, 12));
    }

    @Test
    void shouldCreateTripFromRecurringServiceWithOperationalDriver() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        Viagem viagem = new Viagem();
        viagem.setId(31L);
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));
        viagem.setStatus(com.vanrides.vanrides.model.ViagemStatus.PLANNED);

        when(viagemProvisioningService.criarViagemManual(66L, null, 15L, 7L, LocalDate.of(2026, 3, 12)))
                .thenReturn(viagem);
        when(viagemParadaRepository.countByViagemId(31L)).thenReturn(3L);

        AdminViagemCreateRequest request = new AdminViagemCreateRequest();
        request.setServicoId(15L);
        request.setMotoristaId(7L);
        request.setDataOperacao(LocalDate.of(2026, 3, 12));

        var response = companyAdminViagemService.criarViagem(66L, request);

        assertThat(response.getId()).isEqualTo(31L);
        assertThat(response.getQuantidadeParadas()).isEqualTo(3);
        verify(viagemProvisioningService).criarViagemManual(66L, null, 15L, 7L, LocalDate.of(2026, 3, 12));
    }

    @Test
    void shouldRejectDuplicateTripForSameRouteAndDate() {
        when(viagemProvisioningService.criarViagemManual(66L, 10L, null, null, LocalDate.of(2026, 3, 12)))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Já existe viagem criada para esta rota e data."));

        AdminViagemCreateRequest request = new AdminViagemCreateRequest();
        request.setRotaBaseId(10L);
        request.setDataOperacao(LocalDate.of(2026, 3, 12));

        assertThatThrownBy(() -> companyAdminViagemService.criarViagem(66L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Já existe viagem");
    }

    @Test
    void shouldListTripsUsingDedicatedFilteredRepositoryMethod() {
        Viagem viagem = new Viagem();
        viagem.setId(30L);
        viagem.setRotaBase(rotaBase(10L, company(66L), motorista(7L)));
        viagem.setMotorista(motorista(7L));
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));
        viagem.setStatus(com.vanrides.vanrides.model.ViagemStatus.PLANNED);

        when(viagemRepository.findAllByCompanyIdAndDataOperacaoOrderByIdDesc(66L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of(viagem));
        when(viagemParadaRepository.countByViagemId(30L)).thenReturn(1L);

        var result = companyAdminViagemService.listarViagens(66L, LocalDate.of(2026, 3, 12));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDataOperacao()).isEqualTo(LocalDate.of(2026, 3, 12));
        verify(viagemRepository).findAllByCompanyIdAndDataOperacaoOrderByIdDesc(66L, LocalDate.of(2026, 3, 12));
    }

    @Test
    void shouldGenerateAutomaticTripsForCompanyAdmin() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        Viagem viagem = new Viagem();
        viagem.setId(40L);
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem.setServicoNomeSnapshot("Linha Manhã");

        var ignored = new ViagemProvisioningService.ServicoIgnorado(16L, "Linha Lotada",
                ViagemProvisioningService.MotivoServicoIgnorado.CAPACIDADE_EXCEDIDA,
                "Manifesto operacional acima da capacidade planejada.");
        var blocked = new CompanyTripManifestMaintenanceService.ServicoBloqueado(17L, "Linha Rodando", 44L,
                CompanyTripManifestMaintenanceService.MotivoServicoBloqueado.VIAGEM_EM_ANDAMENTO,
                "A viagem já começou; revise alterações manualmente antes de alterar o manifesto.");
        when(viagemProvisioningService.gerarViagensAutomaticasDaEmpresa(66L, LocalDate.of(2026, 3, 12)))
                .thenReturn(new ViagemProvisioningService.ViagensProvisionadasResult(LocalDate.of(2026, 3, 12),
                        List.of(viagem), 1, 2, 4, 5, 3, List.of(ignored), List.of(blocked)));
        when(viagemParadaRepository.countByViagemId(40L)).thenReturn(5L);

        var response = companyAdminViagemService.gerarViagensAutomaticas(66L, LocalDate.of(2026, 3, 12));

        assertThat(response.getCriadas()).isEqualTo(1);
        assertThat(response.getExistentes()).isEqualTo(2);
        assertThat(response.getAtualizadas()).isEqualTo(4);
        assertThat(response.getBloqueadas()).isEqualTo(5);
        assertThat(response.getIgnoradas()).isEqualTo(3);
        assertThat(response.getServicosIgnorados())
                .extracting(item -> item.getServicoId(), item -> item.getServicoNome(), item -> item.getMotivo())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(16L, "Linha Lotada", "CAPACIDADE_EXCEDIDA"));
        assertThat(response.getServicosIgnorados().get(0).getMensagem()).contains("capacidade");
        assertThat(response.getServicosBloqueados())
                .extracting(item -> item.getServicoId(), item -> item.getServicoNome(), item -> item.getMotivo())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(17L, "Linha Rodando", "VIAGEM_EM_ANDAMENTO"));
        assertThat(response.getServicosBloqueados().get(0).getMensagem()).contains("começou");
        assertThat(response.getViagens()).hasSize(1);
        assertThat(response.getViagens().get(0).getQuantidadeParadas()).isEqualTo(5);
    }

    @Test
    void shouldReturnOperationalTripDetailWithManifestAndPresenceStatus() {
        Company company = company(66L);
        Motorista motorista = motorista(7L);
        RotaBase rotaBase = rotaBase(10L, company, motorista);
        ServicoRecorrente servico = servico(15L, company, rotaBase, motorista);
        Passageiro passageiroConfirmado = passageiro(21L, company, motorista);
        Passageiro passageiroPadrao = passageiro(22L, company, motorista);

        Viagem viagem = viagem(company, motorista, rotaBase);
        viagem.setId(31L);
        viagem.setServicoRecorrente(servico);
        viagem.setServicoNomeSnapshot("Linha Manhã");
        viagem.setTipoOperacaoSnapshot(com.vanrides.vanrides.model.TipoOperacaoServico.IDA);
        viagem.setDestinoFinalNomeSnapshot("Escola Central");
        viagem.setDestinoFinalLatitudeSnapshot(-30.01);
        viagem.setDestinoFinalLongitudeSnapshot(-51.01);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem.setRotaVersao(1);

        ViagemParada parada1 = parada(51L, viagem, passageiroConfirmado, 1, 12);
        ViagemParada parada2 = parada(52L, viagem, passageiroPadrao, 2, 18);
        PresencaServico presenca = presencaServico(company, servico, passageiroConfirmado, PresencaServicoStatus.CONFIRMED);
        presenca.setId(90L);

        when(viagemRepository.findByIdAndCompanyId(31L, 66L)).thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(31L)).thenReturn(List.of(parada1, parada2));
        when(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                List.of(21L, 22L), 66L, 15L, LocalDate.of(2026, 3, 12)
        )).thenReturn(List.of(presenca));
        ViagemRotaGeograficaResponse rotaGeografica = new ViagemRotaGeograficaResponse(
                "GOOGLE_DIRECTIONS_V1",
                "encoded-polyline",
                new ViagemNavegacaoResponse("GOOGLE_MAPS_HANDOFF", "https://maps.google.com/?q=escola", "Origem", "Escola Central", 2),
                List.of(
                        new ViagemRotaWaypointResponse("STOP", 51L, 21L, "Passageiro 21", -30.02, -51.02, 1, true, false),
                        new ViagemRotaWaypointResponse("STOP", 52L, 22L, "Passageiro 22", -30.03, -51.03, 2, false, false)
                ),
                List.of(new ViagemRotaLegResponse(0, "Origem", "Passageiro 21", 1200, 480)),
                2,
                null,
                null
        );
        when(viagemRotaGeograficaService.buildForTrip(viagem, List.of(parada1, parada2), null))
                .thenReturn(rotaGeografica);

        var detail = companyAdminViagemService.buscarDetalhe(66L, 31L);

        assertThat(detail.getId()).isEqualTo(31L);
        assertThat(detail.getServicoNome()).isEqualTo("Linha Manhã");
        assertThat(detail.getDestinoFinal().getNome()).isEqualTo("Escola Central");
        assertThat(detail.getFontePlanejamento()).isEqualTo("SERVICO_RECORRENTE");
        assertThat(detail.isSnapshotOperacional()).isTrue();
        assertThat(detail.getQuantidadeParadas()).isEqualTo(2);
        assertThat(detail.getProximaParada().getId()).isEqualTo(51L);
        assertThat(detail.getParadas().get(0).getPresencaId()).isEqualTo(90L);
        assertThat(detail.getParadas().get(0).getPresencaStatus()).isEqualTo(PresencaServicoStatus.CONFIRMED);
        assertThat(detail.getParadas().get(1).getPresencaId()).isNull();
        assertThat(detail.getParadas().get(1).getPresencaStatus()).isEqualTo(PresencaServicoStatus.UNKNOWN);
        assertThat(detail.getParadas().get(1).isConfirmadoPresenca()).isTrue();
        assertThat(detail.getRotaGeografica().getProvider()).isEqualTo("GOOGLE_DIRECTIONS_V1");
        assertThat(detail.getRotaGeografica().getWaypoints()).hasSize(2);
        assertThat(detail.getRotaGeografica().getNavegacao().getQuantidadeWaypoints()).isEqualTo(2);
    }

    @Test
    void shouldUpdateOperationalDriverOnlyOnTripSnapshot() {
        Company company = company(66L);
        Motorista motoristaAtual = motorista(7L);
        Motorista motoristaNovo = motorista(8L);
        RotaBase rotaBase = rotaBase(10L, company, motoristaAtual);
        Viagem viagem = viagem(company, motoristaAtual, rotaBase);
        viagem.setId(31L);
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem.setRotaVersao(1);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));

        ViagemParada parada = parada(51L, viagem, passageiro(21L, company, motoristaAtual), 1, 12);

        when(viagemRepository.findByIdAndCompanyId(31L, 66L)).thenReturn(Optional.of(viagem));
        when(motoristaRepository.findByIdAndCompanyId(8L, 66L)).thenReturn(Optional.of(motoristaNovo));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(31L)).thenReturn(List.of(parada));
        when(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(List.of(21L), 66L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of());
        when(viagemRepository.save(viagem)).thenReturn(viagem);

        var detail = companyAdminViagemService.trocarMotorista(66L, 31L, 8L);

        assertThat(detail.getMotorista().getId()).isEqualTo(8L);
        assertThat(detail.getRotaVersao()).isEqualTo(2);
        assertThat(rotaBase.getMotorista().getId()).isEqualTo(7L);
        verify(viagemEtaCalculator).recalculate(viagem, List.of(parada));
        verify(viagemParadaRepository).saveAll(List.of(parada));
        verify(viagemRepository).save(viagem);
        verify(viagemRealtimePublisherService).publishTripSnapshot(viagem, List.of(parada));
    }

    @Test
    void shouldCancelPlannedTripWithoutChangingServiceTemplate() {
        Company company = company(66L);
        Motorista motoristaAtual = motorista(7L);
        RotaBase rotaBase = rotaBase(10L, company, motoristaAtual);
        ServicoRecorrente servico = servico(15L, company, rotaBase, motoristaAtual);
        Viagem viagem = viagem(company, motoristaAtual, rotaBase);
        viagem.setId(31L);
        viagem.setServicoRecorrente(servico);
        viagem.setServicoNomeSnapshot("Linha Manhã");
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem.setRotaVersao(2);

        when(viagemRepository.findByIdAndCompanyId(31L, 66L)).thenReturn(Optional.of(viagem));
        when(viagemRepository.save(viagem)).thenReturn(viagem);
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(31L)).thenReturn(List.of());

        var detail = companyAdminViagemService.cancelarViagem(66L, 31L);

        assertThat(detail.getStatus()).isEqualTo(ViagemStatus.CANCELED);
        assertThat(detail.getServicoId()).isEqualTo(15L);
        assertThat(detail.getServicoNome()).isEqualTo("Linha Manhã");
        assertThat(detail.getQuantidadeParadas()).isZero();
        assertThat(servico.isAtivo()).isTrue();
        verify(viagemRepository).save(viagem);
        verify(viagemRealtimePublisherService).publishTripSnapshot(viagem, List.of());
    }

    @Test
    void shouldRejectAdminCancelWhenTripAlreadyStarted() {
        Company company = company(66L);
        Motorista motoristaAtual = motorista(7L);
        Viagem viagem = viagem(company, motoristaAtual, rotaBase(10L, company, motoristaAtual));
        viagem.setId(31L);
        viagem.setStatus(ViagemStatus.IN_PROGRESS);

        when(viagemRepository.findByIdAndCompanyId(31L, 66L)).thenReturn(Optional.of(viagem));

        assertThatThrownBy(() -> companyAdminViagemService.cancelarViagem(66L, 31L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cancelar");

        verify(viagemRepository, never()).save(viagem);
        verify(viagemParadaRepository, never()).findAllWithPassageiroByViagemIdOrderByOrdemAsc(31L);
        verifyNoInteractions(viagemRealtimePublisherService);
    }

    @Test
    void shouldRejectOperationalDriverChangeWhenTripAlreadyStarted() {
        Company company = company(66L);
        Motorista motoristaAtual = motorista(7L);
        Viagem viagem = viagem(company, motoristaAtual, rotaBase(10L, company, motoristaAtual));
        viagem.setId(31L);
        viagem.setStatus(ViagemStatus.IN_PROGRESS);

        when(viagemRepository.findByIdAndCompanyId(31L, 66L)).thenReturn(Optional.of(viagem));

        assertThatThrownBy(() -> companyAdminViagemService.trocarMotorista(66L, 31L, 8L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("troca de motorista");

        verify(motoristaRepository, never()).findByIdAndCompanyId(8L, 66L);
        verify(viagemParadaRepository, never()).saveAll(anyList());
        verifyNoInteractions(viagemRealtimePublisherService);
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

    private RotaBase rotaBase(Long id, Company company, Motorista motorista) {
        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(id);
        rotaBase.setCompany(company);
        rotaBase.setMotorista(motorista);
        rotaBase.setNome("Rota Escolar");
        rotaBase.setAtivo(true);
        return rotaBase;
    }

    private ServicoRecorrente servico(Long id, Company company, RotaBase rotaBase, Motorista motorista) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company);
        servico.setRotaBaseLegada(rotaBase);
        servico.setMotoristaPadrao(motorista);
        servico.setNome("Linha Manhã");
        servico.setPoliticaPresenca(PoliticaPresencaServico.INCLUIR_POR_PADRAO);
        return servico;
    }

    private Viagem viagem(Company company, Motorista motorista, RotaBase rotaBase) {
        Viagem viagem = new Viagem();
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setRotaBase(rotaBase);
        viagem.setStatus(ViagemStatus.PLANNED);
        return viagem;
    }

    private Passageiro passageiro(Long id, Company company, Motorista motorista) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome("Passageiro " + id);
        passageiro.setEmail("passageiro" + id + "@vanrides.dev");
        passageiro.setTelefone("51999990000");
        passageiro.setEndereco("Rua " + id);
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000000");
        passageiro.setCompany(company);
        passageiro.setMotorista(motorista);
        return passageiro;
    }

    private ViagemParada parada(Long id, Viagem viagem, Passageiro passageiro, int ordem, int etaMinutos) {
        ViagemParada parada = new ViagemParada();
        parada.setId(id);
        parada.setViagem(viagem);
        parada.setPassageiro(passageiro);
        parada.setOrdem(ordem);
        parada.setEtaMinutos(etaMinutos);
        return parada;
    }

    private PresencaServico presencaServico(Company company,
                                            ServicoRecorrente servico,
                                            Passageiro passageiro,
                                            PresencaServicoStatus status) {
        PresencaServico presenca = new PresencaServico();
        presenca.setCompany(company);
        presenca.setServicoRecorrente(servico);
        presenca.setPassageiro(passageiro);
        presenca.setDataOperacao(LocalDate.of(2026, 3, 12));
        presenca.setStatus(status);
        return presenca;
    }
}
